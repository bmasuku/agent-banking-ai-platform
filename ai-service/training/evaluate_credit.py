"""Evaluate the credit scorecard against synthetic high-risk labels."""
from __future__ import annotations

import csv
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.credit.engine import evaluate as score_agent
from app.credit.schemas import CreditRequest
from app.governance.registry import CREDIT_MODEL

DATA = Path(__file__).resolve().parent / "sample_data" / "credit_samples.csv"
OUT = ROOT / "models" / "evaluation" / f"{CREDIT_MODEL.name}-{CREDIT_MODEL.version}.json"
SKIP = {"sample_id", "label_high_risk"}
INTS = {"tenure_days", "active_days_30d", "transaction_count_30d", "advances_repaid", "advances_repaid_late",
        "advances_overdue", "advances_defaulted", "flagged_transactions_30d", "confirmed_fraud_count",
        "open_investigations"}


def auc(scores: list[float], labels: list[int]) -> float:
    # Higher score => lower risk, so invert for AUC vs high-risk label.
    inv = [-s for s in scores]
    pairs = sorted(zip(inv, labels), key=lambda x: x[0])
    n_pos = sum(labels)
    n_neg = len(labels) - n_pos
    if n_pos == 0 or n_neg == 0:
        return float("nan")
    rank_sum = sum(i for i, (_, y) in enumerate(pairs, start=1) if y == 1)
    return (rank_sum - n_pos * (n_pos + 1) / 2) / (n_pos * n_neg)


def main() -> None:
    scores: list[float] = []
    labels: list[int] = []
    decisions_rejected = 0
    with DATA.open() as fh:
        for row in csv.DictReader(fh):
            payload = {}
            for k, v in row.items():
                if k in SKIP:
                    continue
                if k == "agent_status":
                    payload[k] = v
                elif k in INTS:
                    payload[k] = int(v)
                else:
                    payload[k] = float(v)
            result = score_agent(CreditRequest.model_validate(payload))
            scores.append(result.score)
            labels.append(int(row["label_high_risk"]))
            if result.decision == "REJECTED":
                decisions_rejected += 1

    flagged = [1 if s < 600 else 0 for s in scores]
    tp = sum(p and y for p, y in zip(flagged, labels))
    fp = sum(p and not y for p, y in zip(flagged, labels))
    fn = sum((not p) and y for p, y in zip(flagged, labels))
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    report = {
        "model": CREDIT_MODEL.name, "version": CREDIT_MODEL.version, "method": CREDIT_MODEL.method,
        "is_trained_model": False, "dataset": DATA.name, "n": len(labels),
        "positive_rate": round(sum(labels) / len(labels), 4),
        "auc_inverted_score": round(auc(scores, labels), 4),
        "high_risk_threshold_score": 600,
        "precision": round(precision, 4), "recall": round(recall, 4),
        "mean_score": round(sum(scores) / len(scores), 2),
        "rejected_share": round(decisions_rejected / len(scores), 4),
        "notes": "Synthetic labels; scorecard points are expert-set, not fitted.",
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
