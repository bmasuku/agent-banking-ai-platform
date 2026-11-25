from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import Field

from app.common.schemas import ApiModel, FeatureContribution, Governance

LiquidityRisk = Literal["NONE", "EFLOAT_SHORTAGE", "CASH_SHORTAGE", "BOTH_SHORTAGE"]


class HourlyFlow(ApiModel):
    hour: datetime
    efloat_net: float
    cash_net: float
    transaction_count: int = Field(default=0, ge=0)


class ForecastRequest(ApiModel):
    agent_id: str | None = None
    agent_code: str | None = None
    current_efloat: float
    current_cash: float
    min_efloat_threshold: float = Field(default=0.0, ge=0)
    min_cash_threshold: float = Field(default=0.0, ge=0)
    horizon_hours: int = Field(default=24, ge=1, le=168)
    as_of: datetime
    timezone: str = "Africa/Harare"
    lookback_days: int = Field(default=14, ge=1, le=90)
    history: list[HourlyFlow] = Field(default_factory=list)


class TrajectoryPoint(ApiModel):
    hour: datetime
    efloat: float
    efloat_lower: float
    efloat_upper: float
    cash: float
    cash_lower: float
    cash_upper: float


class PeakHour(ApiModel):
    hour_of_day: int
    avg_transactions: float
    avg_efloat_net: float
    avg_cash_net: float


class ForecastMetrics(ApiModel):
    observed_days: int
    avg_hourly_efloat_consumption: float = Field(description="Mean e-float outflow per active trading hour")
    avg_hourly_cash_consumption: float = Field(description="Mean cash outflow per active trading hour")
    avg_daily_efloat_consumption: float = Field(description="Mean e-float outflow per day")
    avg_daily_cash_consumption: float = Field(description="Mean cash outflow per day")
    avg_daily_efloat_net_change: float
    avg_daily_cash_net_change: float
    transactions_per_day: float
    transactions_per_active_hour: float
    peak_hours: list[PeakHour]
    peak_efloat_outflow_hour: int | None
    peak_cash_outflow_hour: int | None


class ForecastResponse(ApiModel):
    prediction_id: str
    agent_id: str | None
    agent_code: str | None
    horizon_hours: int
    projected_efloat: float
    projected_cash: float
    min_projected_efloat: float
    min_projected_cash: float
    predicted_efloat_net_flow: float
    predicted_cash_net_flow: float
    hours_to_efloat_stockout: float | None = Field(description="Hours until e-float is projected below the minimum level")
    hours_to_cash_stockout: float | None
    expected_efloat_exhaustion_at: datetime | None = Field(description="When projected e-float reaches zero")
    expected_cash_exhaustion_at: datetime | None
    recommended_efloat_replenishment: float
    recommended_cash_replenishment: float
    liquidity_risk: LiquidityRisk
    metrics: ForecastMetrics
    confidence: float
    trajectory: list[TrajectoryPoint]
    drivers: list[FeatureContribution]
    explanation: str
    model_version: str
    governance: Governance


class AreaForecastRequest(ApiModel):
    area_id: str | None = None
    area_code: str | None = None
    pool_efloat: float | None = Field(default=None, ge=0)
    pool_cash: float | None = Field(default=None, ge=0)
    agents: list[ForecastRequest] = Field(min_length=1, max_length=500)


class AgentForecastSummary(ApiModel):
    agent_id: str | None
    agent_code: str | None
    prediction_id: str
    current_efloat: float
    current_cash: float
    projected_efloat: float
    projected_cash: float
    min_projected_efloat: float
    min_projected_cash: float
    hours_to_efloat_stockout: float | None
    hours_to_cash_stockout: float | None
    expected_efloat_exhaustion_at: datetime | None
    expected_cash_exhaustion_at: datetime | None
    recommended_efloat_replenishment: float
    recommended_cash_replenishment: float
    avg_hourly_efloat_consumption: float
    avg_daily_efloat_consumption: float
    transactions_per_day: float
    liquidity_risk: LiquidityRisk
    confidence: float


class AreaTotals(ApiModel):
    agents: int
    agents_at_risk: int
    current_efloat: float
    current_cash: float
    projected_efloat: float
    projected_cash: float
    predicted_efloat_net_flow: float
    predicted_cash_net_flow: float
    recommended_efloat_replenishment: float
    recommended_cash_replenishment: float
    earliest_efloat_stockout_hours: float | None
    earliest_cash_stockout_hours: float | None
    pool_efloat: float | None
    pool_cash: float | None
    pool_efloat_coverage: float | None = Field(description="Pool e-float / recommended e-float replenishment")
    pool_cash_coverage: float | None


class AreaForecastResponse(ApiModel):
    prediction_id: str
    area_id: str | None
    area_code: str | None
    totals: AreaTotals
    agents: list[AgentForecastSummary]
    explanation: str
    confidence: float
    model_version: str
    governance: Governance
