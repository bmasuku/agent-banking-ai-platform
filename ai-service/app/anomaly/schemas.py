from __future__ import annotations

from datetime import datetime
from typing import Any, Literal

from pydantic import Field

from app.common.aliases import field
from app.common.schemas import ApiModel, Governance

AnomalyType = Literal["UNUSUAL_AMOUNT", "UNUSUAL_FREQUENCY", "SUDDEN_ACTIVITY_INCREASE", "UNUSUAL_REVERSALS",
                      "ABNORMAL_PATTERN", "ABNORMAL_AGENT_PATTERN"]
Severity = Literal["LOW", "MEDIUM", "HIGH"]


class TransactionRecord(ApiModel):
    id: str | None = None
    timestamp: datetime
    type: str
    amount: float = Field(gt=0)
    status: str = "COMPLETED"


class AnomalyRequest(ApiModel):
    agent_id: str | None = None
    agent_code: str | None = None
    as_of: datetime
    timezone: str = "Africa/Harare"
    recent_window_hours: int = Field(default=24, ge=1, le=168)
    transactions: list[TransactionRecord] = Field(default_factory=list, max_length=50_000)


class Anomaly(ApiModel):
    type: AnomalyType
    severity: Severity
    score: float = Field(ge=0, le=1)
    transaction_id: str | None = None
    timestamp: datetime | None = None
    description: str
    evidence: dict[str, Any]


class MethodReport(ApiModel):
    method: str
    applied: bool
    detail: str


class AnomalyResponse(ApiModel):
    prediction_id: str
    agent_id: str | None
    agent_code: str | None
    anomaly_score: float = Field(ge=0, le=1)
    risk_level: Severity | Literal["NONE"]
    anomalies: list[Anomaly]
    counts_by_type: dict[str, int]
    baseline_transactions: int
    recent_transactions: int
    methods: list[MethodReport]
    explanation: str
    confidence: float
    model_version: str
    governance: Governance


class PeerFeatures(ApiModel):
    transactions_7d: int = field("transactions_7d", ge=0)
    volume_7d: float = field("volume_7d", ge=0)
    avg_amount_7d: float = field("avg_amount_7d", ge=0)
    off_hours_share_7d: float = field("off_hours_share_7d", ge=0, le=1)
    withdrawal_share_7d: float = field("withdrawal_share_7d", ge=0, le=1)
    flagged_7d: int = field("flagged_7d", ge=0)
    reversals_7d: int = field("reversals_7d", 0, ge=0)
    distinct_customers_7d: int = field("distinct_customers_7d", ge=0)


class PeerAgent(ApiModel):
    agent_id: str | None = None
    agent_code: str
    features: PeerFeatures


class PeerRequest(ApiModel):
    area_id: str | None = None
    area_code: str | None = None
    agents: list[PeerAgent] = Field(min_length=2, max_length=2000)


class PeerAgentResult(ApiModel):
    agent_id: str | None
    agent_code: str
    anomaly_score: float
    abnormal: bool
    deviations: list[dict[str, Any]]
    explanation: str


class PeerResponse(ApiModel):
    prediction_id: str
    area_id: str | None
    area_code: str | None
    agents: list[PeerAgentResult]
    methods: list[MethodReport]
    explanation: str
    confidence: float
    model_version: str
    governance: Governance
