from __future__ import annotations

from typing import Any, Literal

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from pydantic import Field

from app.common.schemas import ApiModel, ModelInfo
from app.common.security import require_internal_key
from app.governance.registry import list_models

router = APIRouter(prefix="/internal/ai/v1/governance", tags=["Governance"], dependencies=[Depends(require_internal_key)])

StaffRole = Literal["AGENT", "AREA_MANAGER", "RISK_OFFICER", "FINANCE_OFFICER", "ADMIN"]


class HumanDecisionRequest(ApiModel):
    decision: str = Field(min_length=2, max_length=60, description="e.g. CONFIRMED_FRAUD, CLEARED, APPROVED, REJECTED")
    decided_by: str = Field(min_length=1, max_length=120)
    decided_by_role: StaffRole
    notes: str | None = Field(default=None, max_length=4000)
    source_reference: str | None = Field(default=None, max_length=120, description="Backend entity reference")


class HumanDecisionResponse(ApiModel):
    id: str
    prediction_id: str
    decision: str
    decided_by: str
    decided_by_role: str
    model_label: str | None
    agrees_with_model: bool | None
    data_classification: Literal["HUMAN_DECISION"] = "HUMAN_DECISION"


@router.get("/models", response_model=list[ModelInfo], summary="Model registry with versions, methods and evaluations")
def models() -> list[ModelInfo]:
    return list_models()


@router.get("/predictions", summary="List stored predictions (newest first)")
def predictions(request: Request, kind: str | None = Query(default=None, description="FRAUD, CREDIT, FORECAST, ..."),
                subject_id: str | None = None, limit: int = Query(default=50, ge=1, le=200),
                offset: int = Query(default=0, ge=0)) -> dict[str, Any]:
    return request.app.state.governance.list_predictions(kind=kind, subject_id=subject_id, limit=limit, offset=offset)


@router.get("/predictions/{prediction_id}", summary="Full prediction record including inputs and human decisions")
def prediction(request: Request, prediction_id: str) -> dict[str, Any]:
    record = request.app.state.governance.get_prediction(prediction_id)
    if record is None:
        raise HTTPException(status_code=404, detail={"error_code": "PREDICTION_NOT_FOUND", "message": prediction_id})
    return record


@router.post("/predictions/{prediction_id}/decisions", response_model=HumanDecisionResponse, status_code=201,
             summary="Record the human decision taken on a prediction")
def record_decision(request: Request, prediction_id: str, body: HumanDecisionRequest) -> HumanDecisionResponse:
    record = request.app.state.governance.record_decision(
        prediction_id=prediction_id, decision=body.decision, decided_by=body.decided_by,
        decided_by_role=body.decided_by_role, notes=body.notes, source_reference=body.source_reference)
    if record is None:
        raise HTTPException(status_code=404, detail={"error_code": "PREDICTION_NOT_FOUND", "message": prediction_id})
    return HumanDecisionResponse(**record)


@router.get("/summary", summary="Prediction volumes, average confidence and human agreement rates per model")
def summary(request: Request) -> dict[str, Any]:
    return request.app.state.governance.summary()
