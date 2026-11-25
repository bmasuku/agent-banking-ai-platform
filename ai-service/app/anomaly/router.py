from __future__ import annotations

from collections import Counter

from fastapi import APIRouter, Depends, Request

from app.anomaly.engine import compare_peers, detect
from app.anomaly.schemas import AnomalyRequest, AnomalyResponse, PeerRequest, PeerResponse
from app.common.security import require_internal_key
from app.governance import recorder
from app.governance.registry import ANOMALY_MODEL

router = APIRouter(prefix="/internal/ai/v1/anomaly", tags=["Anomaly detection"])
MODEL_VERSION = f"{ANOMALY_MODEL.name}:{ANOMALY_MODEL.version}"


@router.post("/detect", response_model=AnomalyResponse,
             summary="Detect unusual amounts, frequency, activity spikes, reversals and abnormal patterns for one agent")
def detect_agent(request: Request, body: AnomalyRequest, caller: str = Depends(require_internal_key)) -> AnomalyResponse:
    r = detect(body)
    governance = recorder.new_governance(ANOMALY_MODEL, r.confidence, r.confidence_basis)
    response = AnomalyResponse(
        prediction_id=governance.prediction_id, agent_id=body.agent_id, agent_code=body.agent_code, anomaly_score=r.score,
        risk_level=r.risk_level, anomalies=r.anomalies, counts_by_type=dict(Counter(a.type for a in r.anomalies)),
        baseline_transactions=r.baseline_count, recent_transactions=r.recent_count, methods=r.methods,
        explanation=r.explanation, confidence=r.confidence, model_version=MODEL_VERSION, governance=governance)
    recorder.save(request, kind="ANOMALY", spec=ANOMALY_MODEL, governance=governance, subject_type="AGENT",
                  subject_id=body.agent_id, subject_code=body.agent_code, label=r.risk_level, score=r.score,
                  explanation=r.explanation, payload=body, response=response, caller=caller)
    return response


@router.post("/peer-comparison", response_model=PeerResponse,
             summary="Compare agents' 7-day behaviour with their peers to find abnormal agent transaction patterns")
def peer_comparison(request: Request, body: PeerRequest, caller: str = Depends(require_internal_key)) -> PeerResponse:
    r = compare_peers(body)
    governance = recorder.new_governance(ANOMALY_MODEL, r.confidence, r.confidence_basis)
    response = PeerResponse(prediction_id=governance.prediction_id, area_id=body.area_id, area_code=body.area_code,
                            agents=r.agents, methods=r.methods, explanation=r.explanation, confidence=r.confidence,
                            model_version=MODEL_VERSION, governance=governance)
    flagged = sum(1 for a in r.agents if a.abnormal)
    recorder.save(request, kind="ANOMALY_PEER", spec=ANOMALY_MODEL, governance=governance, subject_type="AREA",
                  subject_id=body.area_id, subject_code=body.area_code, label=f"{flagged}_ABNORMAL",
                  score=max((a.anomaly_score for a in r.agents), default=0.0), explanation=r.explanation, payload=body,
                  response=response, caller=caller)
    return response
