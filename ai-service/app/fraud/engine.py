"""Deterministic, explainable fraud risk rules.

Each rule that fires adds a fixed number of points; the risk score is the capped sum (0-100). The weights
are expert-set for the demonstration and have not been learned from labelled data.
"""
from __future__ import annotations

from dataclasses import dataclass, field

from app.common.geo import haversine_km
from app.common.schemas import FeatureContribution
from app.fraud.schemas import FraudRequest, TriggeredRule

CATEGORY_THRESHOLDS = ((90, "CRITICAL"), (60, "HIGH"), (30, "MEDIUM"))
NEAR_KM = 10.0
FAR_KM = 50.0

ACTIONS = {
    "LOW": ("NO_ACTION", "No action required; the transaction is consistent with the agent's normal behaviour."),
    "MEDIUM": ("MONITOR", "Keep the agent under routine monitoring; no immediate review is needed."),
    "HIGH": ("REVIEW_TRANSACTION",
             "A risk officer should review this transaction, check the customer's identity and the agent's recent activity."),
    "CRITICAL": ("OPEN_INVESTIGATION",
                 "A risk officer should open an investigation, review linked transactions and consider restricting the agent "
                 "pending review."),
}


@dataclass
class FraudResult:
    risk_score: int
    risk_category: str
    rules: list[TriggeredRule]
    contributions: list[FeatureContribution]
    explanation: str
    recommended_action: str
    recommended_action_detail: str
    distance_km: float | None
    confidence: float
    confidence_basis: str
    notes: list[str] = field(default_factory=list)


def category_for(score: float) -> str:
    for threshold, name in CATEGORY_THRESHOLDS:
        if score >= threshold:
            return name
    return "LOW"


def distance_km(f: FraudRequest) -> float | None:
    coords = (f.agent_latitude, f.agent_longitude, f.transaction_latitude, f.transaction_longitude)
    if any(c is None for c in coords):
        return None
    return haversine_km(*coords)  # type: ignore[arg-type]


def evaluate(f: FraudRequest) -> FraudResult:
    rules: list[TriggeredRule] = []

    def fire(code: str, points: int, feature: str, value: object, description: str) -> None:
        rules.append(TriggeredRule(code=code, points=points, feature=feature, value=str(value), description=description))

    dist = distance_km(f)
    if dist is not None:
        if dist > FAR_KM:
            fire("LOCATION_FAR_FROM_REGISTERED", 25, "distanceFromRegisteredKm", f"{dist:.1f}",
                 f"Transaction captured {dist:.1f} km from the agent's registered location (more than {FAR_KM:.0f} km)")
        elif dist > NEAR_KM:
            fire("LOCATION_AWAY_FROM_REGISTERED", 12, "distanceFromRegisteredKm", f"{dist:.1f}",
                 f"Transaction captured {dist:.1f} km from the agent's registered location")

    enough_history = f.agent_history_count_30d >= 10
    if enough_history and f.amount_z_score >= 4.0:
        fire("EXTREME_AMOUNT", 20, "amountZScore", f"{f.amount_z_score:.2f}",
             f"Amount {f.amount:,.2f} is {f.amount_z_score:.1f} standard deviations above the agent's 30-day mean "
             f"of {f.agent_avg_amount_30d:,.2f}")
    elif enough_history and (f.amount_z_score >= 2.5 or f.amount_to_avg_ratio >= 5):
        fire("UNUSUAL_AMOUNT", 10, "amountZScore", f"{f.amount_z_score:.2f}",
             f"Amount {f.amount:,.2f} is unusually large for this agent ({f.amount_to_avg_ratio:.1f}x the 30-day average)")

    if f.agent_tx_count_1h >= 8:
        fire("HIGH_FREQUENCY", 15, "agentTxCount1h", f.agent_tx_count_1h,
             f"{f.agent_tx_count_1h} customer transactions at this agent in the last hour")
    elif f.agent_tx_count_1h >= 5:
        fire("ELEVATED_FREQUENCY", 8, "agentTxCount1h", f.agent_tx_count_1h,
             f"{f.agent_tx_count_1h} customer transactions at this agent in the last hour")

    daily_baseline = f.agent_history_count_30d / 30.0
    if f.agent_tx_count_24h >= 10 and daily_baseline > 0 and f.agent_tx_count_24h / daily_baseline >= 3:
        fire("VELOCITY_SPIKE", 12, "agentTxCount24h", f.agent_tx_count_24h,
             f"{f.agent_tx_count_24h} transactions in 24 hours versus a typical {daily_baseline:.1f} per day")

    if f.near_reporting_threshold and f.near_threshold_count_1h >= 2:
        fire("STRUCTURING_PATTERN", 25, "nearThresholdCount1h", f.near_threshold_count_1h,
             f"{f.near_threshold_count_1h} transactions just below a reporting threshold within an hour (possible structuring)")
    elif f.near_reporting_threshold:
        fire("NEAR_REPORTING_THRESHOLD", 6, "amount", f"{f.amount:.2f}", "Amount sits just below a reporting threshold")

    if f.off_hours:
        points = 12 if f.amount_to_avg_ratio > 2 else 8
        fire("OFF_HOURS", points, "hourOfDay", f.hour_of_day,
             f"Transaction at {f.hour_of_day:02d}:00, outside normal trading hours (06:00-21:59)")

    if f.customer_tx_count_24h >= 4:
        fire("CUSTOMER_REPEAT", 12, "customerTxCount24h", f.customer_tx_count_24h,
             f"The customer has already transacted {f.customer_tx_count_24h} times in 24 hours")
    if f.customer_distinct_agents_24h >= 3:
        fire("CUSTOMER_AGENT_HOPPING", 12, "customerDistinctAgents24h", f.customer_distinct_agents_24h,
             f"The customer used {f.customer_distinct_agents_24h} different agents in 24 hours")

    if f.prior_flagged_90d >= 3 or f.agent_open_flags >= 3:
        fire("SUSPICIOUS_HISTORY", 10, "priorFlagged90d", max(f.prior_flagged_90d, f.agent_open_flags),
             f"The agent has {f.prior_flagged_90d} flagged transactions in 90 days and {f.agent_open_flags} unresolved alerts")
    elif f.prior_flagged_90d >= 1 or f.agent_open_flags >= 1:
        fire("PRIOR_ALERTS", 5, "priorFlagged90d", max(f.prior_flagged_90d, f.agent_open_flags),
             "The agent has earlier fraud alerts on record")

    if f.confirmed_fraud_count >= 1:
        fire("CONFIRMED_FRAUD_HISTORY", 20, "confirmedFraudCount", f.confirmed_fraud_count,
             f"{f.confirmed_fraud_count} previously confirmed fraud case(s) linked to this agent")

    if (f.agent_risk_level or "").upper() == "CRITICAL":
        fire("AGENT_RISK_PROFILE", 8, "agentRiskLevel", "CRITICAL", "Agent currently rated CRITICAL credit/risk")
    elif (f.agent_risk_level or "").upper() == "HIGH":
        fire("AGENT_RISK_PROFILE", 4, "agentRiskLevel", "HIGH", "Agent currently rated HIGH credit/risk")

    rules.sort(key=lambda r: r.points, reverse=True)
    score = min(100, sum(r.points for r in rules))
    category = category_for(score)
    action, detail = ACTIONS[category]

    notes: list[str] = []
    coverage = [enough_history, dist is not None, not f.new_customer]
    if dist is None:
        notes.append("Transaction location not supplied; location rules could not be evaluated.")
    if not enough_history:
        notes.append("Fewer than 10 transactions in 30 days; amount-deviation rules were not evaluated.")
    confidence = 0.55 + 0.4 * (sum(coverage) / len(coverage))

    if rules:
        explanation = f"Risk score {score}/100 ({category}). " + "; ".join(r.description for r in rules) + "."
    else:
        explanation = f"Risk score 0/100 (LOW). No fraud-risk rules fired; the activity matches the agent's recent behaviour."
    if notes:
        explanation += " Note: " + " ".join(notes)

    contributions = [FeatureContribution(feature=r.feature, value=r.value, contribution=r.points / 100.0,
                                         direction="INCREASES_RISK", description=r.description) for r in rules]
    return FraudResult(risk_score=score, risk_category=category, rules=rules, contributions=contributions,
                       explanation=explanation, recommended_action=action, recommended_action_detail=detail,
                       distance_km=None if dist is None else round(dist, 2), confidence=round(confidence, 4),
                       confidence_basis="Share of rule families with sufficient input data (history, location, customer)",
                       notes=notes)
