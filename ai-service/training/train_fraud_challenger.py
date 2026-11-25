"""Train an experimental fraud challenger. It is NOT served."""
from __future__ import annotations

import csv
import json
from pathlib import Path

import joblib
import numpy as np
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.metrics import roc_auc_score
from sklearn.model_selection import train_test_split

DATA = Path(__file__).resolve().parent / "sample_data" / "fraud_samples.csv"
MODELS = Path(__file__).resolve().parents[1] / "models"
FEATURES = [
    "amount", "amount_z_score", "amount_to_avg_ratio", "agent_tx_count_1h", "agent_tx_count_24h",
    "agent_history_count_30d", "near_reporting_threshold", "near_threshold_count_1h", "off_hours",
    "customer_tx_count_24h", "customer_distinct_agents_24h", "prior_flagged_90d", "confirmed_fraud_count",
]


def main() -> None:
    rows = list(csv.DictReader(DATA.open()))
    x = np.array([[float(r[f]) for f in FEATURES] for r in rows], dtype=float)
    y = np.array([int(r["label_fraud"]) for r in rows])
    x_train, x_test, y_train, y_test = train_test_split(x, y, test_size=0.3, random_state=42, stratify=y)
    model = GradientBoostingClassifier(random_state=42)
    model.fit(x_train, y_train)
    proba = model.predict_proba(x_test)[:, 1]
    auc = float(roc_auc_score(y_test, proba))
    artefact = MODELS / "fraud-challenger.joblib"
    card = MODELS / "fraud-challenger.model-card.json"
    MODELS.mkdir(parents=True, exist_ok=True)
    joblib.dump({"model": model, "features": FEATURES}, artefact)
    card.write_text(json.dumps({
        "name": "fraud-challenger-gbdt",
        "version": "0.1.0",
        "serving": False,
        "is_trained_model": True,
        "description": ("Experimental GradientBoostingClassifier trained on synthetic samples only. "
                        "Not wired into any API. Do not use for decisions."),
        "artefact": artefact.name,
        "features": FEATURES,
        "evaluation": {"auc": round(auc, 4), "n_train": int(len(y_train)), "n_test": int(len(y_test)),
                       "dataset": "synthetic fraud_samples.csv"},
    }, indent=2))
    print(f"Wrote {artefact} and {card} (AUC={auc:.4f}). Serving=false.")


if __name__ == "__main__":
    main()
