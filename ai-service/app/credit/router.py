from __future__ import annotations

from fastapi import APIRouter, Depends, Request

from app.common.security import require_internal_key
from app.credit.engine import evaluate
from app.credit.schemas import CreditRequest, CreditResponse
from app.governance import recorder
from app.governance.registry import CREDIT_MODEL

router = APIRouter(prefix="/internal/ai/v1/credit", tags=["Credit scoring"])


@router.post("/score", response_model=CreditResponse,
             summary="Explainable 0-1000 credit score with an APPROVED / REJECTED / MANUAL_REVIEW recommendation")
def score(request: Request, body: CreditRequest, caller: str = Depends(require_internal_key)) -> CreditResponse:
    result = evaluate(body)
    governance = recorder.new_governance(CREDIT_MODEL, result.confidence, result.confidence_basis)
    response = CreditResponse(
        prediction_id=governance.prediction_id, credit_score=result.score, risk_category=result.band,
        probability_of_default=result.pd, recommended_limit=result.recommended_limit,
        recommended_advance_amount=result.recommended_advance, eligible=result.eligible, decision=result.decision,
        decision_reasons=result.reasons, factors=result.factors,
        contributing_risk_factors=[c for c in result.factors if c.contribution < 0], explanation=result.explanation,
        confidence=result.confidence, model_version=f"{CREDIT_MODEL.name}:{CREDIT_MODEL.version}", governance=governance)
    recorder.save(request, kind="CREDIT", spec=CREDIT_MODEL, governance=governance, subject_type="AGENT",
                  subject_id=body.agent_id, subject_code=body.agent_code, label=result.decision, score=result.score,
                  explanation=result.explanation, payload=body, response=response, caller=caller)
    return response
