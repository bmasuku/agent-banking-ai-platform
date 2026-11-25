from __future__ import annotations

from typing import Literal

from pydantic import Field

from app.common.aliases import field
from app.common.schemas import ApiModel, FeatureContribution, Governance

RiskCategory = Literal["LOW", "MEDIUM", "HIGH", "CRITICAL"]
RecommendedAction = Literal["NO_ACTION", "MONITOR", "REVIEW_TRANSACTION", "OPEN_INVESTIGATION"]


class FraudRequest(ApiModel):
    """Behavioural features of one prospective transaction, computed by the backend from committed history."""

    agent_id: str | None = None
    agent_code: str | None = None
    transaction_id: str | None = None
    transaction_type: str = "CASH_WITHDRAWAL"
    amount: float = Field(gt=0)
    hour_of_day: int = Field(default=12, ge=0, le=23)
    off_hours: bool = False

    agent_latitude: float | None = Field(default=None, ge=-90, le=90)
    agent_longitude: float | None = Field(default=None, ge=-180, le=180)
    transaction_latitude: float | None = Field(default=None, ge=-90, le=90)
    transaction_longitude: float | None = Field(default=None, ge=-180, le=180)

    agent_tx_count_1h: int = field("agent_tx_count_1h", 0, ge=0)
    agent_tx_count_24h: int = field("agent_tx_count_24h", 0, ge=0)
    agent_history_count_30d: int = field("agent_history_count_30d", 0, ge=0)
    agent_avg_amount_30d: float = field("agent_avg_amount_30d", 0.0, ge=0)
    agent_std_amount_30d: float = field("agent_std_amount_30d", 0.0, ge=0)
    amount_z_score: float = 0.0
    amount_to_avg_ratio: float = 1.0

    near_reporting_threshold: bool = False
    near_threshold_count_1h: int = field("near_threshold_count_1h", 0, ge=0)
    customer_tx_count_24h: int = field("customer_tx_count_24h", 0, ge=0)
    customer_distinct_agents_24h: int = field("customer_distinct_agents_24h", 0, ge=0)
    new_customer: bool = False

    agent_risk_level: str | None = None
    agent_credit_score: int | None = None
    agent_open_flags: int = Field(default=0, ge=0)
    prior_flagged_90d: int = field("prior_flagged_90d", 0, ge=0)
    confirmed_fraud_count: int = Field(default=0, ge=0)


class TriggeredRule(ApiModel):
    code: str
    points: int
    feature: str
    value: str
    description: str


class FraudResponse(ApiModel):
    prediction_id: str
    risk_score: int = Field(ge=0, le=100)
    risk_category: RiskCategory
    fraud_probability: float = Field(ge=0, le=1, description="risk_score / 100; kept for the backend's decision policy")
    risk_level: RiskCategory
    triggered_rules: list[TriggeredRule]
    reason_codes: list[str]
    feature_contributions: list[FeatureContribution]
    explanation: str
    recommended_action: RecommendedAction
    recommended_action_detail: str
    distance_from_registered_location_km: float | None
    confidence: float
    model_version: str
    governance: Governance
