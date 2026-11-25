from __future__ import annotations

from fastapi import APIRouter, Depends, Request

from app.common.security import require_internal_key
from app.fraud.engine import evaluate
from app.fraud.schemas import FraudRequest, FraudResponse
from app.governance import recorder
from app.governance.registry import FRAUD_MODEL

router = APIRouter(prefix="/internal/ai/v1/fraud", tags=["Fraud detection"])


@router.post("/evaluate", response_model=FraudResponse,
             summary="Score a transaction for fraud risk (0-100) with triggered rules and a recommended action")
def evaluate_transaction(request: Request, body: FraudRequest, caller: str = Depends(require_internal_key)) -> FraudResponse:
    result = evaluate(body)
    governance = recorder.new_governance(FRAUD_MODEL, result.confidence, result.confidence_basis)
    response = FraudResponse(
        prediction_id=governance.prediction_id, risk_score=result.risk_score, risk_category=result.risk_category,
        fraud_probability=round(result.risk_score / 100.0, 4), risk_level=result.risk_category,
        triggered_rules=result.rules, reason_codes=[r.code for r in result.rules],
        feature_contributions=result.contributions, explanation=result.explanation,
        recommended_action=result.recommended_action, recommended_action_detail=result.recommended_action_detail,
        distance_from_registered_location_km=result.distance_km, confidence=result.confidence,
        model_version=f"{FRAUD_MODEL.name}:{FRAUD_MODEL.version}", governance=governance)
    recorder.save(request, kind="FRAUD", spec=FRAUD_MODEL, governance=governance, subject_type="AGENT",
                  subject_id=body.agent_id, subject_code=body.agent_code,
                  label=result.risk_category, score=result.risk_score, explanation=result.explanation, payload=body,
                  response=response, caller=caller)
    return response
