"""Sanity-check anomaly detectors on synthetic amount/frequency extremes."""
from __future__ import annotations

import csv
import json
import sys
from datetime import UTC, datetime, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.anomaly.engine import detect
from app.anomaly.schemas import AnomalyRequest, TransactionRecord
from app.governance.registry import ANOMALY_MODEL

DATA = Path(__file__).resolve().parent / "sample_data" / "anomaly_samples.csv"
OUT = ROOT / "models" / "evaluation" / f"{ANOMALY_MODEL.name}-{ANOMALY_MODEL.version}.json"


def main() -> None:
    # Build one long baseline of normal txs, then inject labelled extremes as the recent window.
    as_of = datetime(2024, 2, 1, 12, tzinfo=UTC)
    baseline = []
    start = as_of - timedelta(days=14)
    for i in range(200):
        ts = start + timedelta(hours=i % (14 * 24))
        baseline.append(TransactionRecord(
            id=f"B{i}", timestamp=ts, type="CASH_WITHDRAWAL", amount=100 + (i % 7) * 10, status="COMPLETED"))

    hits = misses = 0
    with DATA.open() as fh:
        for i, row in enumerate(csv.DictReader(fh)):
            if i >= 80:
                break
            recent = list(baseline)
            amount = float(row["amount"])
            ts = as_of - timedelta(minutes=30)
            recent.append(TransactionRecord(
                id=f"R{i}", timestamp=ts, type="CASH_WITHDRAWAL", amount=amount,
                status="REVERSED" if int(row["reversal"]) else "COMPLETED"))
            for j in range(int(row["hour_count"])):
                recent.append(TransactionRecord(
                    id=f"H{i}_{j}", timestamp=as_of - timedelta(minutes=10 + j),
                    type="CASH_DEPOSIT", amount=80, status="COMPLETED"))
            result = detect(AnomalyRequest(
                agent_id="demo", agent_code="DEMO", as_of=as_of, timezone="UTC",
                recent_window_hours=24, transactions=recent))
            flagged = 1 if result.anomalies else 0
            label = int(row["label_anomaly"])
            if flagged == label:
                hits += 1
            else:
                misses += 1

    total = hits + misses
    report = {
        "model": ANOMALY_MODEL.name, "version": ANOMALY_MODEL.version, "method": ANOMALY_MODEL.method,
        "is_trained_model": False, "dataset": DATA.name, "n": total,
        "agreement_with_synthetic_label": round(hits / total, 4) if total else 0.0,
        "notes": ("Isolation Forest is fitted per request on the agent's own baseline; "
                  "agreement is a rough sanity check, not a trained-model metric."),
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
