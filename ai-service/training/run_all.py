"""Generate samples and run every evaluation script."""
from __future__ import annotations

import runpy
from pathlib import Path

HERE = Path(__file__).resolve().parent


def main() -> None:
    for script in (
        "generate_samples.py",
        "evaluate_fraud.py",
        "evaluate_credit.py",
        "evaluate_forecast.py",
        "evaluate_anomaly.py",
        "train_fraud_challenger.py",
    ):
        print(f"\n=== {script} ===")
        runpy.run_path(str(HERE / script), run_name="__main__")


if __name__ == "__main__":
    main()
