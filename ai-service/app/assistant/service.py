from __future__ import annotations

import logging

import httpx
from fastapi import Request

from app.assistant import deterministic
from app.assistant.backend_client import BackendClient
from app.assistant.guardrails import check_prohibited
from app.assistant.llm import chat_with_llm
from app.assistant.schemas import AssistantChatRequest, AssistantChatResponse, ToolCallRecord
from app.common.config import Settings

logger = logging.getLogger(__name__)


def handle_chat(request: Request, body: AssistantChatRequest,
                *, backend: BackendClient | None = None,
                llm_http: httpx.Client | None = None) -> AssistantChatResponse:
    settings: Settings = request.app.state.settings
    store = request.app.state.governance

    refusal = check_prohibited(body.question)
    if refusal:
        interaction_id = store.record_interaction(
            user_id=body.user_id, username=body.username, role=body.role, question=body.question,
            answer=refusal, engine="GUARDRAIL", tools=[], refused=True)
        return AssistantChatResponse(
            interaction_id=interaction_id, answer=refusal, refused=True, engine="GUARDRAIL",
            tools_used=[], data_classifications=[])

    owns_backend = backend is None
    client = backend or BackendClient(settings, body.authorization)
    try:
        if settings.llm_enabled:
            try:
                answer, tools = chat_with_llm(
                    settings, role=body.role, question=body.question, conversation=body.conversation,
                    client=client, agent_id=body.agent_id, http=llm_http)
                engine = f"LLM:{settings.llm_model}"
            except Exception as exc:  # noqa: BLE001 — fall back honestly
                logger.warning("LLM failed (%s); using deterministic router", exc)
                answer, tools = deterministic.answer(
                    body.question, body.role, client, agent_id=body.agent_id)
                engine = "DETERMINISTIC_INTENT_ROUTER_AFTER_LLM_ERROR"
                answer = (f"(LLM unavailable: {exc}. Falling back to the deterministic router.)\n\n{answer}")
        else:
            answer, tools = deterministic.answer(
                body.question, body.role, client, agent_id=body.agent_id)
            engine = "DETERMINISTIC_INTENT_ROUTER"
    finally:
        if owns_backend:
            client.close()

    classifications = sorted({t.classification for t in tools})
    interaction_id = store.record_interaction(
        user_id=body.user_id, username=body.username, role=body.role, question=body.question,
        answer=answer, engine=engine, tools=[t.model_dump() for t in tools], refused=False)
    return AssistantChatResponse(
        interaction_id=interaction_id, answer=answer, refused=False, engine=engine,
        tools_used=tools, data_classifications=classifications)
