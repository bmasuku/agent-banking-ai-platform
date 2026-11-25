from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from app.common.schemas import Method, ModelInfo

MODELS_DIR = Path(__file__).resolve().parents[2] / "models"
EVALUATION_DIR = MODELS_DIR / "evaluation"


@dataclass(frozen=True)
class ModelSpec:
    name: str
    version: str
    method: Method
    description: str
    is_trained_model: bool = False


FRAUD_MODEL = ModelSpec(
    "fraud-risk-rules", "1.0.0", "DETERMINISTIC_RULES",
    "Additive, transparent fraud-typology rules (location, amount, frequency, velocity, structuring, history). "
    "Weights are set by hand, not learned from data.")
CREDIT_MODEL = ModelSpec(
    "credit-scorecard", "1.0.0", "DETERMINISTIC_SCORECARD",
    "Points-based 0-1000 scorecard over repayment, defaults, leverage, earnings, performance, fraud history and tenure. "
    "Points are set by hand, not fitted to default outcomes.")
FORECAST_MODEL = ModelSpec(
    "float-forecaster", "1.0.0", "STATISTICAL",
    "Recency-weighted hour-of-day seasonal profile of net customer flows with an empirical uncertainty band.")
ANOMALY_MODEL = ModelSpec(
    "anomaly-detector", "1.0.0", "STATISTICAL_AND_ISOLATION_FOREST",
    "Robust z-scores, Poisson/binomial tail tests, and an unsupervised Isolation Forest fitted at request time on "
    "the agent's own history (no labelled training data).")
OPTIMISER_MODEL = ModelSpec(
    "float-redistribution-optimiser", "1.0.0", "LINEAR_PROGRAMMING",
    "Linear programme (SciPy HiGHS) minimising projected liquidity shortfall, travel distance and pool usage.")

SERVING_MODELS = [FRAUD_MODEL, CREDIT_MODEL, FORECAST_MODEL, ANOMALY_MODEL, OPTIMISER_MODEL]


def _evaluation(name: str, version: str) -> dict[str, Any] | None:
    path = EVALUATION_DIR / f"{name}-{version}.json"
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text())
    except (OSError, json.JSONDecodeError):
        return None


def list_models() -> list[ModelInfo]:
    models = [ModelInfo(name=m.name, version=m.version, method=m.method, is_trained_model=m.is_trained_model,
                        serving=True, description=m.description, evaluation=_evaluation(m.name, m.version))
              for m in SERVING_MODELS]
    card = MODELS_DIR / "fraud-challenger.model-card.json"
    if card.exists():
        try:
            meta = json.loads(card.read_text())
            models.append(ModelInfo(
                name=meta["name"], version=meta["version"], method="STATISTICAL", is_trained_model=True, serving=False,
                description=meta.get("description", "Experimental challenger model; not used for decisions."),
                evaluation=meta.get("evaluation")))
        except (OSError, json.JSONDecodeError, KeyError):
            pass
    return models
