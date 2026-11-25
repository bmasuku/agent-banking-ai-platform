"""Compare the statistical forecaster to a naive last-day-repeat baseline (MAE)."""
from __future__ import annotations

import csv
import json
import sys
from collections import defaultdict
from datetime import UTC, datetime, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.forecasting.engine import forecast
from app.forecasting.schemas import ForecastRequest, HourlyFlow
from app.governance.registry import FORECAST_MODEL

DATA = Path(__file__).resolve().parent / "sample_data" / "forecast_hourly.csv"
OUT = ROOT / "models" / "evaluation" / f"{FORECAST_MODEL.name}-{FORECAST_MODEL.version}.json"


def main() -> None:
    by_agent: dict[str, list[dict]] = defaultdict(list)
    with DATA.open() as fh:
        for row in csv.DictReader(fh):
            by_agent[row["agent_id"]].append(row)

    model_abs: list[float] = []
    naive_abs: list[float] = []
    for agent_id, rows in by_agent.items():
        rows = sorted(rows, key=lambda r: (int(r["day"]), int(r["hour"])))
        # Hold out the last day; train/profile on earlier days.
        max_day = max(int(r["day"]) for r in rows)
        history_rows = [r for r in rows if int(r["day"]) < max_day]
        actual = [r for r in rows if int(r["day"]) == max_day]
        start = datetime(2024, 1, 1, tzinfo=UTC)
        history = []
        for r in history_rows:
            ts = start + timedelta(days=int(r["day"]), hours=int(r["hour"]))
            history.append(HourlyFlow(hour=ts, efloat_net=float(r["efloat_net"]), cash_net=float(r["cash_net"]),
                                      transaction_count=int(r["transaction_count"])))
        as_of = start + timedelta(days=max_day)
        req = ForecastRequest(
            agent_id=agent_id, agent_code=agent_id, as_of=as_of, timezone="UTC",
            current_efloat=5000, current_cash=3000, min_efloat_threshold=500, min_cash_threshold=300,
            horizon_hours=24, history=history)
        result = forecast(req)
        # Naive: repeat the previous day's hourly nets.
        prev = {int(r["hour"]): r for r in rows if int(r["day"]) == max_day - 1}
        for hour in range(24):
            actual_e = float(actual[hour]["efloat_net"])
            traj = result.trajectory[hour]
            # Forecast stores cumulative balances; recover implied hourly net from adjacent points.
            prev_bal = 5000.0 if hour == 0 else result.trajectory[hour - 1].efloat
            pred_net = traj.efloat - prev_bal
            model_abs.append(abs(pred_net - actual_e))
            naive = float(prev[hour]["efloat_net"]) if hour in prev else 0.0
            naive_abs.append(abs(naive - actual_e))

    report = {
        "model": FORECAST_MODEL.name, "version": FORECAST_MODEL.version, "method": FORECAST_MODEL.method,
        "is_trained_model": False, "dataset": DATA.name,
        "mae_model_efloat_net": round(sum(model_abs) / len(model_abs), 4),
        "mae_naive_last_day_efloat_net": round(sum(naive_abs) / len(naive_abs), 4),
        "improvement_vs_naive": round(
            (sum(naive_abs) - sum(model_abs)) / sum(naive_abs), 4) if sum(naive_abs) else 0.0,
        "n_hourly_points": len(model_abs),
        "notes": "Hold-out last day per agent; naive baseline repeats the previous day's hourly nets.",
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
