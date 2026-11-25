from __future__ import annotations

from typing import Literal

from pydantic import Field

from app.common.aliases import field
from app.common.schemas import ApiModel, FeatureContribution, Governance

CreditDecision = Literal["APPROVED", "REJECTED", "MANUAL_REVIEW"]
RiskCategory = Literal["LOW", "MEDIUM", "HIGH", "CRITICAL"]


class CreditRequest(ApiModel):
    """Repayment, earnings and behaviour features of an agent, computed by the backend from its records."""

    agent_id: str | None = None
    agent_code: str | None = None
    tenure_days: int = Field(default=0, ge=0, description="Agent account age in days")
    active_days_30d: int = field("active_days_30d", 0, ge=0, le=31)
    transaction_count_30d: int = field("transaction_count_30d", 0, ge=0)
    transaction_volume_30d: float = field("transaction_volume_30d", 0.0, ge=0)
    avg_daily_commission_30d: float = field("avg_daily_commission_30d", 0.0, ge=0)
    commission_volatility: float = Field(default=0.0, ge=0)
    efloat_balance: float = 0.0
    cash_balance: float = 0.0
    outstanding_principal: float = Field(default=0.0, ge=0)
    outstanding_interest: float = Field(default=0.0, ge=0)
    advances_repaid: int = Field(default=0, ge=0)
    advances_repaid_late: int = Field(default=0, ge=0)
    advances_overdue: int = Field(default=0, ge=0)
    advances_defaulted: int = Field(default=0, ge=0, description="Advances more than 30 days past due")
    flagged_transactions_30d: int = field("flagged_transactions_30d", 0, ge=0)
    confirmed_fraud_count: int = Field(default=0, ge=0)
    open_investigations: int = Field(default=0, ge=0)
    agent_status: str = "ACTIVE"
    requested_amount: float | None = Field(default=None, gt=0)


class CreditResponse(ApiModel):
    prediction_id: str
    credit_score: int = Field(ge=0, le=1000)
    risk_category: RiskCategory
    probability_of_default: float = Field(description="Score-implied, uncalibrated estimate (no default outcomes used)")
    recommended_limit: float = Field(description="Total credit line suggested before platform policy caps")
    recommended_advance_amount: float = Field(description="New advance suggested now (limit less outstanding principal)")
    eligible: bool
    decision: CreditDecision
    decision_reasons: list[str]
    factors: list[FeatureContribution]
    contributing_risk_factors: list[FeatureContribution]
    explanation: str
    confidence: float
    model_version: str
    governance: Governance
