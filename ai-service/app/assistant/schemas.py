from __future__ import annotations

from typing import Any, Literal

from pydantic import Field

from app.common.schemas import ApiModel

Role = Literal["AGENT", "AREA_MANAGER", "RISK_OFFICER", "FINANCE_OFFICER", "ADMIN"]


class AssistantMessage(ApiModel):
    role: Literal["user", "assistant"]
    content: str


class AssistantChatRequest(ApiModel):
    user_id: str | None = None
    username: str | None = None
    role: Role
    agent_id: str | None = Field(default=None, description="Bound agent id when the caller is an AGENT")
    area_id: str | None = Field(default=None, description="Bound area id when the caller is an AREA_MANAGER")
    question: str = Field(min_length=1, max_length=4000)
    conversation: list[AssistantMessage] = Field(default_factory=list, max_length=20)
    authorization: str = Field(description="User Bearer token forwarded by Spring Boot so tool calls inherit RBAC")


class ToolCallRecord(ApiModel):
    name: str
    arguments: dict[str, Any] = Field(default_factory=dict)
    classification: Literal["VERIFIED_FACT", "PREDICTION", "ERROR"]
    summary: str
    ok: bool = True


class AssistantChatResponse(ApiModel):
    interaction_id: str
    answer: str
    refused: bool = False
    engine: str
    tools_used: list[ToolCallRecord] = Field(default_factory=list)
    data_classifications: list[str] = Field(default_factory=list)
    disclaimer: str = ("Answers combine VERIFIED_FACT data from the platform API with PREDICTION outputs from the AI "
                       "models. Predictions are not verified facts and must not be treated as ledger balances or "
                       "final risk decisions. The assistant cannot execute transactions, approve advances, change "
                       "balances or classify fraud.")
