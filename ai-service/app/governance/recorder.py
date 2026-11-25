from __future__ import annotations

import uuid
from datetime import UTC, datetime
from typing import Any

from fastapi import Request
from pydantic import BaseModel

from app.common.schemas import Governance
from app.governance.registry import ModelSpec


def new_governance(spec: ModelSpec, confidence: float, confidence_basis: str) -> Governance:
    return Governance(prediction_id=str(uuid.uuid4()), model_name=spec.name, model_version=spec.version,
                      method=spec.method, is_trained_model=spec.is_trained_model, confidence=round(confidence, 4),
                      confidence_basis=confidence_basis, generated_at=datetime.now(UTC))


def save(request: Request, *, kind: str, spec: ModelSpec, governance: Governance, subject_type: str,
         subject_id: str | None, subject_code: str | None, label: str | None, score: float | None,
         explanation: str, payload: BaseModel, response: BaseModel, caller: str | None) -> None:
    """Persists the full input and output of a prediction for audit and later comparison with human decisions."""
    request.app.state.governance.record_prediction(
        prediction_id=governance.prediction_id, kind=kind, model_name=spec.name, model_version=spec.version,
        method=spec.method, subject_type=subject_type, subject_id=subject_id, subject_code=subject_code, label=label,
        score=score, confidence=governance.confidence, explanation=explanation,
        input_data=payload.model_dump(mode="json"), output_data=response.model_dump(mode="json"), caller=caller,
        created_at=governance.generated_at)


def dump(model: BaseModel) -> dict[str, Any]:
    return model.model_dump(mode="json")
