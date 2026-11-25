"""Evaluate the serving fraud rules against synthetic labels. Not a claim of ML training."""
from __future__ import annotations

import csv
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.fraud.engine import evaluate
from app.fraud.schemas import FraudRequest
from app.governance.registry import FRAUD_MODEL

DATA = Path(__file__).resolve().parent / "sample_data" / "fraud_samples.csv"
OUT = ROOT / "models" / "evaluation" / f"{FRAUD_MODEL.name}-{FRAUD_MODEL.version}.json"

BOOLS = {"off_hours", "near_reporting_threshold", "new_customer"}
INTS = {"hour_of_day", "agent_tx_count_1h", "agent_tx_count_24h", "agent_history_count_30d",
        "near_threshold_count_1h", "customer_tx_count_24h", "customer_distinct_agents_24h",
        "agent_open_flags", "prior_flagged_90d", "confirmed_fraud_count"}
SKIP = {"sample_id", "label_fraud"}


def auc(scores: list[float], labels: list[int]) -> float:
    pairs = sorted(zip(scores, labels), key=lambda x: x[0])
    n_pos = sum(labels)
    n_neg = len(labels) - n_pos
    if n_pos == 0 or n_neg == 0:
        return float("nan")
    rank_sum = sum(i for i, (_, y) in enumerate(pairs, start=1) if y == 1)
    return (rank_sum - n_pos * (n_pos + 1) / 2) / (n_pos * n_neg)


def main() -> None:
    scores: list[float] = []
    labels: list[int] = []
    with DATA.open() as fh:
        for row in csv.DictReader(fh):
            payload = {}
            for k, v in row.items():
                if k in SKIP:
                    continue
                if k in BOOLS:
                    payload[k] = bool(int(v))
                elif k in INTS:
                    payload[k] = int(v)
                elif k == "agent_risk_level":
                    payload[k] = v
                else:
                    payload[k] = float(v)
            payload["agent_id"] = "demo"
            payload["agent_code"] = "DEMO"
            result = evaluate(FraudRequest.model_validate(payload))
            scores.append(result.risk_score)
            labels.append(int(row["label_fraud"]))

    preds = [1 if s >= 60 else 0 for s in scores]
    tp = sum(p and y for p, y in zip(preds, labels))
    fp = sum(p and not y for p, y in zip(preds, labels))
    fn = sum((not p) and y for p, y in zip(preds, labels))
    tn = sum((not p) and not y for p, y in zip(preds, labels))
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    report = {
        "model": FRAUD_MODEL.name, "version": FRAUD_MODEL.version, "method": FRAUD_MODEL.method,
        "is_trained_model": False, "dataset": DATA.name, "n": len(labels),
        "positive_rate": round(sum(labels) / len(labels), 4),
        "auc_risk_score": round(auc(scores, labels), 4),
        "threshold_risk_score": 60,
        "precision": round(precision, 4), "recall": round(recall, 4),
        "confusion": {"tp": tp, "fp": fp, "tn": tn, "fn": fn},
        "notes": "Synthetic generative labels independent of the rule engine.",
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
