from __future__ import annotations

from fastapi import APIRouter, Depends, Request

from app.common.security import require_internal_key
from app.common.stats import round_money
from app.forecasting.engine import ForecastResult, forecast
from app.forecasting.schemas import (AgentForecastSummary, AreaForecastRequest, AreaForecastResponse, AreaTotals,
                                     ForecastRequest, ForecastResponse)
from app.governance import recorder
from app.governance.registry import FORECAST_MODEL

router = APIRouter(prefix="/internal/ai/v1/float", tags=["Float forecasting"])
MODEL_VERSION = f"{FORECAST_MODEL.name}:{FORECAST_MODEL.version}"


def _predict(request: Request, body: ForecastRequest, caller: str) -> tuple[ForecastResult, ForecastResponse]:
    r = forecast(body)
    governance = recorder.new_governance(FORECAST_MODEL, r.confidence, r.confidence_basis)
    response = ForecastResponse(
        prediction_id=governance.prediction_id, agent_id=body.agent_id, agent_code=body.agent_code,
        horizon_hours=body.horizon_hours, projected_efloat=r.projected_efloat, projected_cash=r.projected_cash,
        min_projected_efloat=r.min_efloat, min_projected_cash=r.min_cash, predicted_efloat_net_flow=r.net_efloat,
        predicted_cash_net_flow=r.net_cash, hours_to_efloat_stockout=r.stockout_efloat_h,
        hours_to_cash_stockout=r.stockout_cash_h, expected_efloat_exhaustion_at=r.exhaustion_efloat_at,
        expected_cash_exhaustion_at=r.exhaustion_cash_at, recommended_efloat_replenishment=r.topup_efloat,
        recommended_cash_replenishment=r.topup_cash, liquidity_risk=r.risk, metrics=r.metrics, confidence=r.confidence,
        trajectory=r.trajectory, drivers=r.drivers, explanation=r.explanation, model_version=MODEL_VERSION,
        governance=governance)
    recorder.save(request, kind="FORECAST", spec=FORECAST_MODEL, governance=governance, subject_type="AGENT",
                  subject_id=body.agent_id, subject_code=body.agent_code, label=r.risk, score=r.min_efloat,
                  explanation=r.explanation, payload=body, response=response, caller=caller)
    return r, response


@router.post("/forecast", response_model=ForecastResponse,
             summary="Forecast an agent's e-float and cash with consumption metrics, exhaustion time and top-up need")
def agent_forecast(request: Request, body: ForecastRequest, caller: str = Depends(require_internal_key)) -> ForecastResponse:
    return _predict(request, body, caller)[1]


@router.post("/forecast/area", response_model=AreaForecastResponse,
             summary="Area-wide forecast: every agent's outlook plus area totals and pool coverage")
def area_forecast(request: Request, body: AreaForecastRequest, caller: str = Depends(require_internal_key)) -> AreaForecastResponse:
    summaries: list[AgentForecastSummary] = []
    for agent in body.agents:
        r, resp = _predict(request, agent, caller)
        summaries.append(AgentForecastSummary(
            agent_id=agent.agent_id, agent_code=agent.agent_code, prediction_id=resp.prediction_id,
            current_efloat=agent.current_efloat, current_cash=agent.current_cash, projected_efloat=r.projected_efloat,
            projected_cash=r.projected_cash, min_projected_efloat=r.min_efloat, min_projected_cash=r.min_cash,
            hours_to_efloat_stockout=r.stockout_efloat_h, hours_to_cash_stockout=r.stockout_cash_h,
            expected_efloat_exhaustion_at=r.exhaustion_efloat_at, expected_cash_exhaustion_at=r.exhaustion_cash_at,
            recommended_efloat_replenishment=r.topup_efloat, recommended_cash_replenishment=r.topup_cash,
            avg_hourly_efloat_consumption=r.metrics.avg_hourly_efloat_consumption,
            avg_daily_efloat_consumption=r.metrics.avg_daily_efloat_consumption,
            transactions_per_day=r.metrics.transactions_per_day, liquidity_risk=r.risk, confidence=r.confidence))

    summaries.sort(key=lambda s: (min(x for x in (s.hours_to_efloat_stockout, s.hours_to_cash_stockout, 1e9) if x is not None),
                                  s.agent_code or ""))
    need_e = sum(s.recommended_efloat_replenishment for s in summaries)
    need_c = sum(s.recommended_cash_replenishment for s in summaries)
    stock_e = [s.hours_to_efloat_stockout for s in summaries if s.hours_to_efloat_stockout is not None]
    stock_c = [s.hours_to_cash_stockout for s in summaries if s.hours_to_cash_stockout is not None]
    totals = AreaTotals(
        agents=len(summaries), agents_at_risk=sum(1 for s in summaries if s.liquidity_risk != "NONE"),
        current_efloat=round_money(sum(s.current_efloat for s in summaries)),
        current_cash=round_money(sum(s.current_cash for s in summaries)),
        projected_efloat=round_money(sum(s.projected_efloat for s in summaries)),
        projected_cash=round_money(sum(s.projected_cash for s in summaries)),
        predicted_efloat_net_flow=round_money(sum(s.projected_efloat - s.current_efloat for s in summaries)),
        predicted_cash_net_flow=round_money(sum(s.projected_cash - s.current_cash for s in summaries)),
        recommended_efloat_replenishment=need_e, recommended_cash_replenishment=need_c,
        earliest_efloat_stockout_hours=min(stock_e) if stock_e else None,
        earliest_cash_stockout_hours=min(stock_c) if stock_c else None,
        pool_efloat=body.pool_efloat, pool_cash=body.pool_cash,
        pool_efloat_coverage=round(body.pool_efloat / need_e, 2) if body.pool_efloat is not None and need_e > 0 else None,
        pool_cash_coverage=round(body.pool_cash / need_c, 2) if body.pool_cash is not None and need_c > 0 else None)

    at_risk = [s.agent_code or s.agent_id for s in summaries if s.liquidity_risk != "NONE"]
    explanation = (f"{totals.agents} agent(s) forecast; {totals.agents_at_risk} projected to fall below minimum operating "
                   f"levels" + (f" ({', '.join(str(a) for a in at_risk)})" if at_risk else "") + ". "
                   f"Area e-float is expected to move by {totals.predicted_efloat_net_flow:,.2f} and cash by "
                   f"{totals.predicted_cash_net_flow:,.2f}. Recommended replenishment: e-float {need_e:,.0f}, cash {need_c:,.0f}.")
    if totals.pool_efloat_coverage is not None and totals.pool_efloat_coverage < 1:
        explanation += " The area e-float pool cannot cover all recommended top-ups; peer exchanges should be considered."
    if totals.pool_cash_coverage is not None and totals.pool_cash_coverage < 1:
        explanation += " The area cash pool cannot cover all recommended top-ups."

    confidence = sum(s.confidence for s in summaries) / len(summaries)
    governance = recorder.new_governance(FORECAST_MODEL, confidence, "Mean confidence of the individual agent forecasts")
    response = AreaForecastResponse(prediction_id=governance.prediction_id, area_id=body.area_id, area_code=body.area_code,
                                    totals=totals, agents=summaries, explanation=explanation,
                                    confidence=governance.confidence, model_version=MODEL_VERSION, governance=governance)
    recorder.save(request, kind="FORECAST_AREA", spec=FORECAST_MODEL, governance=governance, subject_type="AREA",
                  subject_id=body.area_id, subject_code=body.area_code, label=f"{totals.agents_at_risk}_AT_RISK",
                  score=need_e, explanation=explanation, payload=body, response=response, caller=caller)
    return response
