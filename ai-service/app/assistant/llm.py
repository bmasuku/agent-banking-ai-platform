"""OpenAI-compatible tool-calling loop. Falls back cleanly when the provider errors."""
from __future__ import annotations

import json
from typing import Any

import httpx

from app.assistant.backend_client import BackendClient
from app.assistant.schemas import AssistantMessage, ToolCallRecord
from app.assistant.tools import invoke, openai_tools, tools_for_role
from app.common.config import Settings

SYSTEM_PROMPT = """You are the Agent Banking platform assistant.
You help Agents, Area Managers, Risk Officers and Finance Officers understand authorised platform data.
Rules:
1. Only use the provided tools. Never invent balances, scores or statuses.
2. Label every number you cite as VERIFIED_FACT (from a tool marked that way) or PREDICTION.
3. Never execute, approve, reject, transfer, deposit, withdraw, freeze, or classify fraud. Direct the user to the action screen.
4. If a tool returns an error (for example 403), say the user is not authorised for that data.
5. Keep answers concise and operational.
6. Predictions are not verified facts and must not be presented as ledger balances."""


def chat_with_llm(settings: Settings, *, role: str, question: str, conversation: list[AssistantMessage],
                  client: BackendClient, agent_id: str | None,
                  http: httpx.Client | None = None) -> tuple[str, list[ToolCallRecord]]:
    tools = tools_for_role(role)
    by_name = {t.name: t for t in tools}
    messages: list[dict[str, Any]] = [{"role": "system", "content": SYSTEM_PROMPT}]
    for turn in conversation[-10:]:
        messages.append({"role": turn.role, "content": turn.content})
    messages.append({"role": "user", "content": question})

    records: list[ToolCallRecord] = []
    owns = http is None
    http = http or httpx.Client(timeout=settings.llm_timeout_seconds)
    try:
        for _ in range(settings.llm_max_tool_rounds):
            payload = {
                "model": settings.llm_model,
                "messages": messages,
                "tools": openai_tools(role),
                "tool_choice": "auto",
                "temperature": 0.2,
            }
            response = http.post(
                f"{settings.llm_base_url.rstrip('/')}/chat/completions",
                headers={"Authorization": f"Bearer {settings.llm_api_key}", "Content-Type": "application/json"},
                json=payload)
            if response.status_code >= 400:
                raise RuntimeError(f"LLM provider returned {response.status_code}: {response.text[:300]}")
            choice = response.json()["choices"][0]["message"]
            tool_calls = choice.get("tool_calls") or []
            if not tool_calls:
                content = (choice.get("content") or "").strip()
                return content or "I could not produce an answer.", records
            messages.append(choice)
            for call in tool_calls:
                fn = call.get("function") or {}
                name = fn.get("name") or ""
                raw_args = fn.get("arguments") or "{}"
                try:
                    args = json.loads(raw_args) if isinstance(raw_args, str) else dict(raw_args)
                except json.JSONDecodeError:
                    args = {}
                tool = by_name.get(name)
                if tool is None:
                    summary = f"Tool '{name}' is not available for role {role}"
                    ok, data = False, None
                    classification: str = "ERROR"
                else:
                    ok, summary, data = invoke(tool, client, args, agent_id=agent_id)
                    classification = "ERROR" if not ok else tool.classification
                records.append(ToolCallRecord(
                    name=name, arguments=args, classification=classification, summary=summary, ok=ok))  # type: ignore[arg-type]
                messages.append({
                    "role": "tool",
                    "tool_call_id": call.get("id") or name,
                    "content": json.dumps({"classification": classification, "summary": summary, "data": data}, default=str)[:8000],
                })
        return ("I reached the tool-call limit before finishing. Please narrow the question.", records)
    finally:
        if owns:
            http.close()
