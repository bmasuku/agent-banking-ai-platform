"""Synthetic labelled samples with an independent ground truth (not the serving rule engine)."""
from __future__ import annotations

import argparse
import csv
import random
from pathlib import Path

DATA = Path(__file__).resolve().parent / "sample_data"


def _write(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def fraud_rows(n: int, rng: random.Random) -> list[dict]:
    rows = []
    for i in range(n):
        distance = abs(rng.gauss(5, 20))
        amount_z = rng.gauss(0, 1.2)
        freq_1h = max(0, int(rng.gauss(2, 2)))
        structuring = rng.random() < 0.08
        confirmed = 1 if rng.random() < 0.05 else 0
        avg = max(5.0, rng.lognormvariate(4, 0.5))
        std = max(1.0, rng.uniform(10, 80))
        amount = round(max(5.0, avg + amount_z * std), 2)
        history = rng.randint(5, 400)
        is_fraud = int((distance > 40 and amount_z > 2.0) or structuring or (confirmed and amount_z > 1.5))
        rows.append({
            "sample_id": f"F{i:04d}",
            "amount": amount,
            "hour_of_day": rng.randint(0, 23),
            "off_hours": int(rng.random() < 0.2),
            "agent_tx_count_1h": freq_1h,
            "agent_tx_count_24h": freq_1h * 4 + rng.randint(0, 5),
            "agent_history_count_30d": history,
            "agent_avg_amount_30d": round(avg, 2),
            "agent_std_amount_30d": round(std, 2),
            "amount_z_score": round(amount_z, 3),
            "amount_to_avg_ratio": round(amount / avg, 3),
            "near_reporting_threshold": int(structuring or rng.random() < 0.05),
            "near_threshold_count_1h": 2 if structuring else rng.randint(0, 1),
            "customer_tx_count_24h": rng.randint(0, 6),
            "customer_distinct_agents_24h": rng.randint(1, 5),
            "new_customer": int(rng.random() < 0.1),
            "agent_risk_level": rng.choice(["LOW", "MEDIUM", "HIGH", "CRITICAL"]),
            "agent_open_flags": rng.randint(0, 4),
            "prior_flagged_90d": rng.randint(0, 8),
            "confirmed_fraud_count": confirmed,
            "agent_latitude": -17.8,
            "agent_longitude": 31.0,
            "transaction_latitude": round(-17.8 + distance / 111.0, 5),
            "transaction_longitude": 31.0,
            "label_fraud": is_fraud,
        })
    return rows


def credit_rows(n: int, rng: random.Random) -> list[dict]:
    rows = []
    for i in range(n):
        tenure = rng.randint(5, 900)
        repaid = rng.randint(0, 12)
        late = rng.randint(0, 6)
        defaulted = rng.randint(0, 2) if late > 2 else 0
        overdue = rng.randint(0, 2) if late > 1 else 0
        fraud = rng.randint(0, 2) if rng.random() < 0.1 else 0
        volume = max(100.0, rng.lognormvariate(8, 0.7))
        commission = max(0.5, abs(rng.gauss(8, 3)))
        risky = int(defaulted > 0 or overdue > 0 or fraud > 0 or (tenure < 45 and repaid == 0))
        rows.append({
            "sample_id": f"C{i:04d}",
            "tenure_days": tenure,
            "active_days_30d": min(30, rng.randint(0, 30)),
            "transaction_count_30d": rng.randint(0, 400),
            "transaction_volume_30d": round(volume, 2),
            "avg_daily_commission_30d": round(commission, 2),
            "commission_volatility": round(abs(rng.gauss(0.6, 0.3)), 3),
            "outstanding_principal": round(rng.uniform(0, 2000), 2),
            "outstanding_interest": round(rng.uniform(0, 100), 2),
            "advances_repaid": repaid,
            "advances_repaid_late": late,
            "advances_overdue": overdue,
            "advances_defaulted": defaulted,
            "flagged_transactions_30d": rng.randint(0, 6),
            "confirmed_fraud_count": fraud,
            "open_investigations": 1 if fraud and rng.random() < 0.5 else 0,
            "agent_status": rng.choice(["ACTIVE"] * 8 + ["UNDER_REVIEW", "FROZEN"]),
            "label_high_risk": risky,
        })
    return rows


def forecast_rows(n_agents: int, days: int, rng: random.Random) -> list[dict]:
    rows = []
    for a in range(n_agents):
        base_e = rng.uniform(-40, -5)
        base_c = rng.uniform(-30, -5)
        for d in range(days):
            for h in range(24):
                peak = 1.6 if 10 <= h <= 14 else (0.4 if h < 7 or h > 20 else 1.0)
                rows.append({
                    "agent_id": f"A{a:02d}",
                    "day": d,
                    "hour": h,
                    "efloat_net": round(base_e * peak + rng.gauss(0, 8), 2),
                    "cash_net": round(base_c * peak + rng.gauss(0, 6), 2),
                    "transaction_count": max(0, int(rng.gauss(3 * peak, 1.5))),
                })
    return rows


def anomaly_rows(n: int, rng: random.Random) -> list[dict]:
    rows = []
    for i in range(n):
        is_anom = int(rng.random() < 0.12)
        amount = rng.lognormvariate(5.5, 0.4) * (8 if is_anom and rng.random() < 0.5 else 1)
        hour_count = rng.randint(8, 20) if is_anom and rng.random() < 0.5 else max(0, int(rng.gauss(2, 1)))
        rows.append({
            "sample_id": f"N{i:04d}",
            "amount": round(amount, 2),
            "hour_count": hour_count,
            "reversal": int(is_anom and rng.random() < 0.3),
            "off_hours": int(rng.random() < 0.15),
            "label_anomaly": is_anom,
        })
    return rows


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    rng = random.Random(args.seed)
    for name, rows in (
        ("fraud_samples.csv", fraud_rows(400, rng)),
        ("credit_samples.csv", credit_rows(400, rng)),
        ("forecast_hourly.csv", forecast_rows(8, 21, rng)),
        ("anomaly_samples.csv", anomaly_rows(500, rng)),
    ):
        _write(DATA / name, rows)
    print(f"Wrote samples under {DATA}")


if __name__ == "__main__":
    main()
