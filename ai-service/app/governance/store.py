from __future__ import annotations

import json
import os
import uuid
from datetime import UTC, datetime
from typing import Any

from sqlalchemy import (Boolean, Column, DateTime, Float, ForeignKey, Index, MetaData, String, Table, Text, create_engine,
                        func, select)
from sqlalchemy.engine import Engine

metadata = MetaData()

predictions = Table(
    "ai_predictions", metadata,
    Column("id", String(36), primary_key=True),
    Column("kind", String(40), nullable=False),
    Column("model_name", String(80), nullable=False),
    Column("model_version", String(40), nullable=False),
    Column("method", String(60), nullable=False),
    Column("subject_type", String(40), nullable=False),
    Column("subject_id", String(64)),
    Column("subject_code", String(40)),
    Column("label", String(60)),
    Column("score", Float),
    Column("confidence", Float, nullable=False),
    Column("explanation", Text),
    Column("input_json", Text, nullable=False),
    Column("output_json", Text, nullable=False),
    Column("caller", String(80)),
    Column("created_at", DateTime(timezone=True), nullable=False),
    Index("ix_ai_predictions_kind_created", "kind", "created_at"),
    Index("ix_ai_predictions_subject", "subject_id"),
)

human_decisions = Table(
    "ai_human_decisions", metadata,
    Column("id", String(36), primary_key=True),
    Column("prediction_id", String(36), ForeignKey("ai_predictions.id"), nullable=False),
    Column("decision", String(60), nullable=False),
    Column("decided_by", String(120), nullable=False),
    Column("decided_by_role", String(40), nullable=False),
    Column("notes", Text),
    Column("source_reference", String(120)),
    Column("model_label", String(60)),
    Column("agrees_with_model", Boolean),
    Column("decided_at", DateTime(timezone=True), nullable=False),
    Index("ix_ai_human_decisions_prediction", "prediction_id"),
)

assistant_interactions = Table(
    "ai_assistant_interactions", metadata,
    Column("id", String(36), primary_key=True),
    Column("user_id", String(64)),
    Column("username", String(120)),
    Column("role", String(40), nullable=False),
    Column("question", Text, nullable=False),
    Column("answer", Text, nullable=False),
    Column("engine", String(80), nullable=False),
    Column("tools_json", Text, nullable=False),
    Column("refused", Boolean, nullable=False),
    Column("created_at", DateTime(timezone=True), nullable=False),
)

FRAUD_FLAG_LABELS = {"HIGH", "CRITICAL"}
FRAUD_CONFIRM = {"CONFIRMED_FRAUD"}
FRAUD_CLEAR = {"CLEARED", "NO_FRAUD_FOUND"}


def _now() -> datetime:
    return datetime.now(UTC)


def _dumps(value: Any) -> str:
    return json.dumps(value, default=str, separators=(",", ":"))


def agreement(kind: str, model_label: str | None, decision: str) -> bool | None:
    """Whether a human decision agrees with the model output; None when the comparison is not meaningful."""
    if model_label is None:
        return None
    decision = decision.upper()
    if kind == "FRAUD":
        flagged = model_label in FRAUD_FLAG_LABELS
        if decision in FRAUD_CONFIRM:
            return flagged
        if decision in FRAUD_CLEAR:
            return not flagged
        return None
    if kind == "CREDIT":
        if model_label == "MANUAL_REVIEW" or decision not in {"APPROVED", "REJECTED"}:
            return None
        return model_label == decision
    return None


class GovernanceStore:
    """Persists predictions, human decisions and assistant interactions (SQLite by default, any SQLAlchemy URL)."""

    def __init__(self, database_url: str):
        if database_url.startswith("sqlite:///"):
            path = database_url.removeprefix("sqlite:///")
            if path and path != ":memory:":
                os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
        self.engine: Engine = create_engine(database_url, future=True, pool_pre_ping=True)
        metadata.create_all(self.engine)

    def record_prediction(self, *, kind: str, model_name: str, model_version: str, method: str, subject_type: str,
                          subject_id: str | None, subject_code: str | None, label: str | None, score: float | None,
                          confidence: float, explanation: str, input_data: Any, output_data: Any, caller: str | None,
                          prediction_id: str | None = None, created_at: datetime | None = None) -> str:
        pid = prediction_id or str(uuid.uuid4())
        with self.engine.begin() as conn:
            conn.execute(predictions.insert().values(
                id=pid, kind=kind, model_name=model_name, model_version=model_version, method=method,
                subject_type=subject_type, subject_id=subject_id, subject_code=subject_code, label=label, score=score,
                confidence=confidence, explanation=explanation, input_json=_dumps(input_data),
                output_json=_dumps(output_data), caller=caller, created_at=created_at or _now()))
        return pid

    def get_prediction(self, prediction_id: str) -> dict[str, Any] | None:
        with self.engine.connect() as conn:
            row = conn.execute(select(predictions).where(predictions.c.id == prediction_id)).mappings().first()
            if row is None:
                return None
            decisions = conn.execute(select(human_decisions).where(human_decisions.c.prediction_id == prediction_id)
                                     .order_by(human_decisions.c.decided_at)).mappings().all()
        result = self._prediction_row(row, full=True)
        result["human_decisions"] = [dict(d) for d in decisions]
        return result

    def list_predictions(self, *, kind: str | None = None, subject_id: str | None = None, limit: int = 50,
                         offset: int = 0) -> dict[str, Any]:
        query = select(predictions)
        count = select(func.count()).select_from(predictions)
        if kind:
            query = query.where(predictions.c.kind == kind.upper())
            count = count.where(predictions.c.kind == kind.upper())
        if subject_id:
            query = query.where(predictions.c.subject_id == subject_id)
            count = count.where(predictions.c.subject_id == subject_id)
        query = query.order_by(predictions.c.created_at.desc()).limit(limit).offset(offset)
        with self.engine.connect() as conn:
            rows = conn.execute(query).mappings().all()
            total = conn.execute(count).scalar_one()
        return {"items": [self._prediction_row(r, full=False) for r in rows], "total": total, "limit": limit, "offset": offset}

    def record_decision(self, *, prediction_id: str, decision: str, decided_by: str, decided_by_role: str,
                        notes: str | None, source_reference: str | None) -> dict[str, Any] | None:
        with self.engine.begin() as conn:
            row = conn.execute(select(predictions.c.kind, predictions.c.label)
                               .where(predictions.c.id == prediction_id)).first()
            if row is None:
                return None
            kind, label = row
            record = {
                "id": str(uuid.uuid4()), "prediction_id": prediction_id, "decision": decision.upper(),
                "decided_by": decided_by, "decided_by_role": decided_by_role, "notes": notes,
                "source_reference": source_reference, "model_label": label,
                "agrees_with_model": agreement(kind, label, decision), "decided_at": _now(),
            }
            conn.execute(human_decisions.insert().values(**record))
        return record

    def record_interaction(self, *, user_id: str | None, username: str | None, role: str, question: str, answer: str,
                           engine: str, tools: list[dict[str, Any]], refused: bool) -> str:
        iid = str(uuid.uuid4())
        with self.engine.begin() as conn:
            conn.execute(assistant_interactions.insert().values(
                id=iid, user_id=user_id, username=username, role=role, question=question, answer=answer, engine=engine,
                tools_json=_dumps(tools), refused=refused, created_at=_now()))
        return iid

    def summary(self) -> dict[str, Any]:
        with self.engine.connect() as conn:
            by_model = conn.execute(
                select(predictions.c.kind, predictions.c.model_name, predictions.c.model_version,
                       func.count().label("predictions"), func.avg(predictions.c.confidence).label("avg_confidence"))
                .group_by(predictions.c.kind, predictions.c.model_name, predictions.c.model_version)).mappings().all()
            decisions = conn.execute(
                select(predictions.c.kind, func.count().label("decisions"),
                       func.sum(func.cast(human_decisions.c.agrees_with_model == True, Float)).label("agreements"),  # noqa: E712
                       func.count(human_decisions.c.agrees_with_model).label("comparable"))
                .select_from(human_decisions.join(predictions, predictions.c.id == human_decisions.c.prediction_id))
                .group_by(predictions.c.kind)).mappings().all()
            interactions = conn.execute(select(func.count(), func.sum(func.cast(assistant_interactions.c.refused, Float)))
                                        .select_from(assistant_interactions)).first()
        return {
            "models": [{**dict(r), "avg_confidence": round(float(r["avg_confidence"] or 0), 4)} for r in by_model],
            "human_decisions": [{
                "kind": d["kind"], "decisions": d["decisions"], "comparable": d["comparable"],
                "agreement_rate": round(float(d["agreements"] or 0) / d["comparable"], 4) if d["comparable"] else None,
            } for d in decisions],
            "assistant": {"interactions": interactions[0] or 0, "refusals": int(interactions[1] or 0)},
        }

    @staticmethod
    def _prediction_row(row: Any, full: bool) -> dict[str, Any]:
        data = {k: row[k] for k in ("id", "kind", "model_name", "model_version", "method", "subject_type", "subject_id",
                                     "subject_code", "label", "score", "confidence", "explanation", "caller", "created_at")}
        data["data_classification"] = "PREDICTION"
        if full:
            data["input"] = json.loads(row["input_json"])
            data["output"] = json.loads(row["output_json"])
        return data
