from __future__ import annotations

from typing import Any, Literal

from pydantic import Field

from app.common.schemas import ApiModel, Governance


class AgentPosition(ApiModel):
    agent_id: str | None = None
    agent_code: str
    latitude: float | None = Field(default=None, ge=-90, le=90)
    longitude: float | None = Field(default=None, ge=-180, le=180)
    efloat: float
    cash: float
    min_efloat: float = Field(ge=0)
    min_cash: float = Field(ge=0)
    expected_efloat_net_flow: float = Field(
        default=0.0, description="Expected e-float change over the horizon; pass (min projected - current) for a cautious plan")
    expected_cash_net_flow: float = 0.0
    hours_to_efloat_stockout: float | None = None
    hours_to_cash_stockout: float | None = None
    forecast_confidence: float | None = Field(default=None, ge=0, le=1)
    active: bool = True


class OptimisationRequest(ApiModel):
    area_id: str | None = None
    area_code: str | None = None
    pool_efloat: float = Field(ge=0)
    pool_cash: float = Field(ge=0)
    horizon_hours: int = Field(default=24, ge=1, le=168)
    max_peer_distance_km: float = Field(default=15.0, gt=0)
    safety_buffer: float = Field(default=1.2, ge=1.0, le=3.0, description="Target = minimum level x buffer")
    peer_cost_per_km: float = Field(default=0.002, ge=0, description="Relative cost per unit moved per km between agents")
    pool_cost: float = Field(default=0.05, ge=0, description="Relative cost per unit drawn from the area pool")
    min_transfer: float = Field(default=50.0, ge=0)
    agents: list[AgentPosition] = Field(min_length=1, max_length=300)


class PeerExchange(ApiModel):
    efloat_provider_agent_id: str | None
    efloat_provider_agent_code: str
    cash_provider_agent_id: str | None
    cash_provider_agent_code: str
    direction: Literal["EFLOAT_FOR_CASH"] = "EFLOAT_FOR_CASH"
    amount: float
    distance_km: float
    rationale: str


class PoolReplenishment(ApiModel):
    agent_id: str | None
    agent_code: str
    type: Literal["EFLOAT", "CASH"]
    amount: float
    urgency: float
    rationale: str


class AgentPlan(ApiModel):
    agent_id: str | None
    agent_code: str
    projected_efloat: float
    projected_cash: float
    target_efloat: float
    target_cash: float
    efloat_need: float
    cash_need: float
    efloat_surplus: float
    cash_surplus: float
    efloat_after_plan: float
    cash_after_plan: float
    unmet_efloat: float
    unmet_cash: float


class PlanTotals(ApiModel):
    efloat_need: float
    cash_need: float
    efloat_from_peers: float
    cash_from_peers: float
    efloat_from_pool: float
    cash_from_pool: float
    unmet_efloat: float
    unmet_cash: float
    pool_efloat_remaining: float
    pool_cash_remaining: float
    peer_distance_km: float


class OptimisationResponse(ApiModel):
    prediction_id: str
    area_id: str | None
    area_code: str | None
    peer_exchanges: list[PeerExchange]
    pool_replenishments: list[PoolReplenishment]
    agents: list[AgentPlan]
    totals: PlanTotals
    solver: dict[str, Any]
    explanation: str
    execution_note: str = ("Recommendations only. Area managers execute replenishments and agents propose/accept exchanges "
                           "through the platform; nothing is executed by the AI service.")
    confidence: float
    model_version: str
    governance: Governance
