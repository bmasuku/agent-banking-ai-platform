from __future__ import annotations

from fastapi import APIRouter, Depends, Request

from app.common.security import require_internal_key
from app.governance import recorder
from app.governance.registry import OPTIMISER_MODEL
from app.optimisation.engine import optimise
from app.optimisation.schemas import OptimisationRequest, OptimisationResponse

router = APIRouter(prefix="/internal/ai/v1/float", tags=["Float optimisation"])


@router.post("/optimise", response_model=OptimisationResponse,
             summary="Recommend peer exchanges and area-pool top-ups that minimise projected shortfalls")
def optimise_area(request: Request, body: OptimisationRequest, caller: str = Depends(require_internal_key)) -> OptimisationResponse:
    r = optimise(body)
    governance = recorder.new_governance(OPTIMISER_MODEL, r.confidence, r.confidence_basis)
    response = OptimisationResponse(
        prediction_id=governance.prediction_id, area_id=body.area_id, area_code=body.area_code, peer_exchanges=r.exchanges,
        pool_replenishments=r.replenishments, agents=r.agents, totals=r.totals, solver=r.solver, explanation=r.explanation,
        confidence=r.confidence, model_version=f"{OPTIMISER_MODEL.name}:{OPTIMISER_MODEL.version}", governance=governance)
    recorder.save(request, kind="OPTIMISATION", spec=OPTIMISER_MODEL, governance=governance, subject_type="AREA",
                  subject_id=body.area_id, subject_code=body.area_code, label=r.solver["status"],
                  score=r.totals.unmet_efloat + r.totals.unmet_cash, explanation=r.explanation, payload=body,
                  response=response, caller=caller)
    return response
