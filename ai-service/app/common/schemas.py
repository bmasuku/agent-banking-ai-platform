from __future__ import annotations

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field

DISCLAIMER = (
    "This is a model prediction, not a verified fact. Verified facts come from the platform ledger; "
    "decisions are taken by the platform's policies and authorised staff."
)

Method = Literal[
    "DETERMINISTIC_RULES",
    "DETERMINISTIC_SCORECARD",
    "STATISTICAL",
    "STATISTICAL_AND_ISOLATION_FOREST",
    "LINEAR_PROGRAMMING",
]


class ApiModel(BaseModel):
    model_config = ConfigDict(extra="ignore", populate_by_name=True)


class FeatureContribution(ApiModel):
    """Same shape as the Spring Boot ``FeatureContribution`` record."""

    feature: str
    value: str
    contribution: float
    direction: str
    description: str


class Governance(ApiModel):
    """Attached to every prediction so consumers can tell model output from verified facts."""

    prediction_id: str
    model_name: str
    model_version: str
    method: Method
    is_trained_model: bool = Field(
        default=False,
        description="False: the result comes from deterministic rules or statistics, not a model trained on labelled outcomes",
    )
    confidence: float = Field(ge=0, le=1)
    confidence_basis: str
    data_classification: Literal["PREDICTION"] = "PREDICTION"
    verified_facts_source: str = "Inputs supplied by the Spring Boot backend from its ledger and operational records"
    generated_at: datetime
    disclaimer: str = DISCLAIMER


class ModelInfo(ApiModel):
    name: str
    version: str
    method: Method
    is_trained_model: bool
    serving: bool
    description: str
    evaluation: dict[str, Any] | None = None
