"""Anomaly detection with transparent statistics plus an unsupervised Isolation Forest.

The Isolation Forest is fitted at request time on the agent's own baseline transactions (or on the peer group)
and only scores how isolated a point is relative to that data. It is not trained on labelled fraud and does not
"know" fraud patterns.
"""
from __future__ import annotations

import math
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from zoneinfo import ZoneInfo

import numpy as np
from sklearn.ensemble import IsolationForest

from app.anomaly.schemas import Anomaly, AnomalyRequest, MethodReport, PeerAgentResult, PeerRequest, TransactionRecord
from app.common.stats import binomial_upper_tail, clamp, poisson_upper_tail, robust_z

AMOUNT_Z = 3.5
FREQ_P = 1e-3
SPIKE_P = 1e-2
REVERSAL_P = 1e-2
IF_MIN_BASELINE = 50
IF_MIN_PEERS = 10
CUSTOMER_TYPES = ("CASH_DEPOSIT", "CASH_WITHDRAWAL", "AGENT_TRANSFER_TO_CUSTOMER")
SEVERITY_RANK = {"NONE": 0, "LOW": 1, "MEDIUM": 2, "HIGH": 3}


def _aware(dt: datetime) -> datetime:
    return dt if dt.tzinfo is not None else dt.replace(tzinfo=UTC)


@dataclass
class AgentAnomalyResult:
    anomalies: list[Anomaly]
    methods: list[MethodReport]
    baseline_count: int
    recent_count: int
    score: float
    risk_level: str
    explanation: str
    confidence: float
    confidence_basis: str


def detect(req: AnomalyRequest) -> AgentAnomalyResult:
    zone = ZoneInfo(req.timezone)
    as_of = _aware(req.as_of)
    cutoff = as_of - timedelta(hours=req.recent_window_hours)
    txs = sorted((t for t in req.transactions if _aware(t.timestamp) <= as_of), key=lambda t: _aware(t.timestamp))
    baseline = [t for t in txs if _aware(t.timestamp) < cutoff]
    recent = [t for t in txs if _aware(t.timestamp) >= cutoff]
    baseline_days = max(1.0, (cutoff - _aware(baseline[0].timestamp)).total_seconds() / 86400) if baseline else 0.0

    anomalies: list[Anomaly] = []
    methods: list[MethodReport] = []
    methods.append(_amounts(baseline, recent, anomalies))
    methods.append(_frequency(baseline, recent, baseline_days, zone, anomalies))
    methods.append(_activity_spike(baseline, recent, baseline_days, req.recent_window_hours, anomalies))
    methods.append(_reversals(baseline, recent, anomalies))
    methods.append(_isolation_forest(txs, baseline, recent, zone, anomalies))

    anomalies.sort(key=lambda a: (SEVERITY_RANK[a.severity], a.score), reverse=True)
    score = max((a.score for a in anomalies), default=0.0)
    risk = max((a.severity for a in anomalies), key=lambda s: SEVERITY_RANK[s], default="NONE")
    if anomalies:
        counts = Counter(a.type for a in anomalies)
        explanation = (f"{len(anomalies)} anomaly signal(s) in the last {req.recent_window_hours}h: "
                       + ", ".join(f"{k} x{v}" for k, v in counts.items()) + ". Most significant: "
                       + anomalies[0].description + ".")
    else:
        explanation = (f"No anomalies detected in {len(recent)} transaction(s) over the last {req.recent_window_hours}h "
                       f"compared with {len(baseline)} baseline transaction(s).")
    skipped = [m.method for m in methods if not m.applied]
    if skipped:
        explanation += " Not applied (insufficient data): " + ", ".join(skipped) + "."
    confidence = clamp(0.4 + 0.5 * min(1.0, len(baseline) / 200), 0.4, 0.9)
    return AgentAnomalyResult(anomalies=anomalies, methods=methods, baseline_count=len(baseline), recent_count=len(recent),
                              score=round(score, 4), risk_level=risk, explanation=explanation,
                              confidence=round(confidence, 4),
                              confidence_basis="Size of the agent's baseline history (200+ transactions = highest)")


def _amounts(baseline: list[TransactionRecord], recent: list[TransactionRecord], out: list[Anomaly]) -> MethodReport:
    by_type: dict[str, list[float]] = defaultdict(list)
    for t in baseline:
        by_type[t.type].append(t.amount)
    pooled = [t.amount for t in baseline]
    if len(pooled) < 10:
        return MethodReport(method="robust_z_amount", applied=False, detail="Fewer than 10 baseline transactions")
    for t in recent:
        sample = by_type[t.type] if len(by_type[t.type]) >= 10 else pooled
        z = robust_z(t.amount, sample)
        if z >= AMOUNT_Z:
            median = float(np.median(sample))
            out.append(Anomaly(
                type="UNUSUAL_AMOUNT", severity="HIGH" if z >= 6 else "MEDIUM", score=round(clamp(z / 10, 0, 1), 4),
                transaction_id=t.id, timestamp=t.timestamp,
                description=f"{t.type} of {t.amount:,.2f} is {z:.1f} robust standard deviations above the typical "
                            f"{median:,.2f}",
                evidence={"robust_z": round(z, 2), "baseline_median": round(median, 2), "baseline_size": len(sample)}))
    return MethodReport(method="robust_z_amount", applied=True,
                        detail=f"Modified z-score (median/MAD) against {len(pooled)} baseline amounts, threshold {AMOUNT_Z}")


def _frequency(baseline: list[TransactionRecord], recent: list[TransactionRecord], baseline_days: float, zone: ZoneInfo,
               out: list[Anomaly]) -> MethodReport:
    if baseline_days < 3:
        return MethodReport(method="poisson_hourly_frequency", applied=False, detail="Less than 3 days of baseline history")
    per_hour = Counter(_aware(t.timestamp).astimezone(zone).hour for t in baseline)
    buckets: dict[datetime, int] = Counter(_aware(t.timestamp).astimezone(zone).replace(minute=0, second=0, microsecond=0)
                                           for t in recent)
    for bucket, count in sorted(buckets.items()):
        expected = max(per_hour[bucket.hour] / baseline_days, 0.05)
        p = poisson_upper_tail(count, expected)
        if count >= 3 and p < FREQ_P:
            out.append(Anomaly(
                type="UNUSUAL_FREQUENCY", severity="HIGH" if p < 1e-6 else "MEDIUM",
                score=round(clamp(-math.log10(max(p, 1e-12)) / 12, 0, 1), 4), timestamp=bucket,
                description=f"{count} transactions in the {bucket:%H}:00 hour versus {expected:.2f} expected for that hour",
                evidence={"count": count, "expected": round(expected, 3), "p_value": float(f"{p:.3g}")}))
    return MethodReport(method="poisson_hourly_frequency", applied=True,
                        detail=f"Poisson tail test per clock hour against {baseline_days:.1f} baseline days, p < {FREQ_P}")


def _activity_spike(baseline: list[TransactionRecord], recent: list[TransactionRecord], baseline_days: float,
                    window_hours: int, out: list[Anomaly]) -> MethodReport:
    if baseline_days < 3:
        return MethodReport(method="poisson_activity_spike", applied=False, detail="Less than 3 days of baseline history")
    expected = max(len(baseline) / baseline_days * window_hours / 24, 0.5)
    observed = len(recent)
    p = poisson_upper_tail(observed, expected)
    ratio = observed / expected
    base_volume = sum(t.amount for t in baseline) / baseline_days * window_hours / 24
    recent_volume = sum(t.amount for t in recent)
    if observed >= 5 and ratio >= 2 and p < SPIKE_P:
        out.append(Anomaly(
            type="SUDDEN_ACTIVITY_INCREASE", severity="HIGH" if ratio >= 4 else "MEDIUM",
            score=round(clamp(ratio / 8, 0, 1), 4),
            description=f"{observed} transactions in the last {window_hours}h, {ratio:.1f}x the usual {expected:.1f}",
            evidence={"observed": observed, "expected": round(expected, 2), "ratio": round(ratio, 2),
                      "p_value": float(f"{p:.3g}"), "recent_volume": round(recent_volume, 2),
                      "expected_volume": round(base_volume, 2)}))
    return MethodReport(method="poisson_activity_spike", applied=True,
                        detail=f"Recent count vs baseline rate (expected {expected:.1f}), ratio >= 2 and p < {SPIKE_P}")


def _reversals(baseline: list[TransactionRecord], recent: list[TransactionRecord], out: list[Anomaly]) -> MethodReport:
    if not recent:
        return MethodReport(method="binomial_reversal_rate", applied=False, detail="No recent transactions")
    base_rev = sum(1 for t in baseline if t.status.upper() == "REVERSED")
    rate = (base_rev + 0.5) / (len(baseline) + 1)
    rev = sum(1 for t in recent if t.status.upper() == "REVERSED")
    p = binomial_upper_tail(rev, len(recent), rate)
    if rev >= 2 and p < REVERSAL_P:
        out.append(Anomaly(
            type="UNUSUAL_REVERSALS", severity="HIGH" if p < 1e-4 else "MEDIUM",
            score=round(clamp(-math.log10(max(p, 1e-12)) / 8, 0, 1), 4),
            description=f"{rev} of {len(recent)} recent transactions were reversed versus a baseline rate of {rate:.1%}",
            evidence={"reversed": rev, "recent": len(recent), "baseline_rate": round(rate, 4), "p_value": float(f"{p:.3g}")}))
    return MethodReport(method="binomial_reversal_rate", applied=True,
                        detail=f"Binomial tail test of {rev} reversal(s) against baseline rate {rate:.2%}")


def _tx_features(txs: list[TransactionRecord], zone: ZoneInfo) -> np.ndarray:
    rows = []
    previous: datetime | None = None
    for t in txs:
        ts = _aware(t.timestamp)
        local = ts.astimezone(zone)
        hour = local.hour + local.minute / 60
        gap = 24 * 60.0 if previous is None else max(0.0, (ts - previous).total_seconds() / 60)
        previous = ts
        rows.append([math.log1p(t.amount), math.sin(2 * math.pi * hour / 24), math.cos(2 * math.pi * hour / 24),
                     *(1.0 if t.type == c else 0.0 for c in CUSTOMER_TYPES), math.log1p(gap),
                     1.0 if t.status.upper() != "COMPLETED" else 0.0])
    return np.asarray(rows, dtype=float)


def _isolation_forest(all_txs: list[TransactionRecord], baseline: list[TransactionRecord], recent: list[TransactionRecord],
                      zone: ZoneInfo, out: list[Anomaly]) -> MethodReport:
    if len(baseline) < IF_MIN_BASELINE or not recent:
        return MethodReport(method="isolation_forest", applied=False,
                            detail=f"Needs at least {IF_MIN_BASELINE} baseline transactions and one recent transaction")
    features = _tx_features(all_txs, zone)
    n_base = len(baseline)
    forest = IsolationForest(n_estimators=200, random_state=42, contamination="auto").fit(features[:n_base])
    base_scores = forest.score_samples(features[:n_base])
    recent_scores = forest.score_samples(features[n_base:])
    p1 = float(np.percentile(base_scores, 1))
    p01 = float(np.percentile(base_scores, 0.1))
    spread = float(np.median(base_scores) - p01) or 1e-6
    for t, s in zip(recent, recent_scores):
        if s < p1:
            out.append(Anomaly(
                type="ABNORMAL_PATTERN", severity="HIGH" if s < p01 else "MEDIUM",
                score=round(clamp((float(np.median(base_scores)) - s) / (2 * spread), 0, 1), 4), transaction_id=t.id,
                timestamp=t.timestamp,
                description=f"{t.type} of {t.amount:,.2f} at {_aware(t.timestamp).astimezone(zone):%H:%M} combines amount, "
                            f"time, type and spacing in a way rarer than 99% of this agent's history",
                evidence={"isolation_score": round(float(s), 4), "baseline_p1": round(p1, 4)}))
    return MethodReport(method="isolation_forest", applied=True,
                        detail=f"Unsupervised Isolation Forest (200 trees, seed 42) fitted on {n_base} baseline transactions; "
                               "flags points more isolated than the 1st percentile of the baseline")


PEER_FEATURES = ("transactions_7d", "volume_7d", "avg_amount_7d", "off_hours_share_7d", "withdrawal_share_7d", "flagged_7d",
                 "reversals_7d", "distinct_customers_7d")


@dataclass
class PeerResult:
    agents: list[PeerAgentResult]
    methods: list[MethodReport]
    explanation: str
    confidence: float
    confidence_basis: str


def compare_peers(req: PeerRequest) -> PeerResult:
    matrix = np.asarray([[getattr(a.features, f) for f in PEER_FEATURES] for a in req.agents], dtype=float)
    methods = [MethodReport(method="robust_z_peer", applied=len(req.agents) >= 4,
                            detail=f"Modified z-score of each feature across {len(req.agents)} agents, threshold {AMOUNT_Z}"
                            if len(req.agents) >= 4 else "Needs at least 4 agents")]
    if_scores: np.ndarray | None = None
    if len(req.agents) >= IF_MIN_PEERS:
        scaled = (matrix - matrix.mean(axis=0)) / (matrix.std(axis=0) + 1e-9)
        forest = IsolationForest(n_estimators=200, random_state=42, contamination="auto").fit(scaled)
        if_scores = forest.score_samples(scaled)
        methods.append(MethodReport(method="isolation_forest_peer", applied=True,
                                    detail="Unsupervised Isolation Forest across the peer group (standardised features)"))
    else:
        methods.append(MethodReport(method="isolation_forest_peer", applied=False, detail=f"Needs at least {IF_MIN_PEERS} agents"))

    results: list[PeerAgentResult] = []
    for i, agent in enumerate(req.agents):
        deviations = []
        if len(req.agents) >= 4:
            for j, name in enumerate(PEER_FEATURES):
                others = np.delete(matrix[:, j], i)
                z = robust_z(matrix[i, j], others)
                median = float(np.median(others))
                if abs(z) >= AMOUNT_Z and abs(matrix[i, j] - median) > 1e-9:
                    deviations.append({"feature": name, "value": round(float(matrix[i, j]), 4),
                                       "peer_median": round(median, 4), "robust_z": round(float(z), 2)})
        if_flag = False
        if if_scores is not None:
            if_flag = bool(if_scores[i] < np.percentile(if_scores, 5))
        score = max([min(1.0, abs(d["robust_z"]) / 10) for d in deviations] + [0.6 if if_flag else 0.0])
        abnormal = bool(deviations) or if_flag
        explanation = ("Activity is in line with peers." if not abnormal else
                       "Differs from peers: " + "; ".join(f"{d['feature']} {d['value']} vs peer median {d['peer_median']}"
                                                         for d in deviations)
                       + (" (also isolated by the peer Isolation Forest)" if if_flag else "") + ".")
        results.append(PeerAgentResult(agent_id=agent.agent_id, agent_code=agent.agent_code, anomaly_score=round(score, 4),
                                       abnormal=abnormal, deviations=deviations, explanation=explanation))
    results.sort(key=lambda r: r.anomaly_score, reverse=True)
    flagged = [r.agent_code for r in results if r.abnormal]
    explanation = (f"{len(flagged)} of {len(results)} agent(s) show abnormal patterns relative to their peers"
                   + (f": {', '.join(flagged)}." if flagged else "."))
    confidence = clamp(0.3 + 0.05 * len(req.agents), 0.3, 0.85)
    return PeerResult(agents=results, methods=methods, explanation=explanation, confidence=round(confidence, 4),
                      confidence_basis="Peer group size (larger groups give more reliable comparisons)")
