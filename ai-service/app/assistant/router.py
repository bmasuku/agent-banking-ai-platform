from __future__ import annotations

from fastapi import APIRouter, Depends, Request

from app.assistant.schemas import AssistantChatRequest, AssistantChatResponse
from app.assistant.service import handle_chat
from app.common.security import require_internal_key

router = APIRouter(prefix="/internal/ai/v1/assistant", tags=["Assistant"])


@router.post("/chat", response_model=AssistantChatResponse,
             summary="Role-aware assistant that retrieves authorised data via Spring Boot (read-only)")
def chat(request: Request, body: AssistantChatRequest,
         _caller: str = Depends(require_internal_key)) -> AssistantChatResponse:
    return handle_chat(request, body)
