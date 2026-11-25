from __future__ import annotations

import httpx
import pytest
from fastapi.testclient import TestClient

from app.api import create_app
from app.assistant.backend_client import BackendClient
from app.assistant.guardrails import check_prohibited
from app.assistant.schemas import AssistantChatRequest
from app.assistant.service import handle_chat
from app.common.config import Settings


def test_guardrail_blocks_transfer():
    msg = check_prohibited("Please send money to customer 0772123456")
    assert msg is not None
    assert "cannot" in msg.lower()


def test_guardrail_allows_balance_question():
    assert check_prohibited("What is my e-float balance?") is None


def _backend_transport(request: httpx.Request) -> httpx.Response:
    if request.url.path.endswith("/balances"):
        return httpx.Response(200, json={
            "agentCode": "AG001",
            "balances": {"efloat": 1200.5, "cash": 800.0, "commissionPayable": 45.0, "advanceOutstanding": 0},
            "efloatLow": False, "cashLow": False,
        })
    if request.url.path.endswith("/summary"):
        return httpx.Response(200, json={
            "agentCode": "AG001", "accruedPayable": 45.0, "accruedCount": 3,
            "lifetimeNetDisbursed": 200.0,
        })
    if "/predictions/" in request.url.path:
        return httpx.Response(200, json={
            "agentCode": "AG001", "projectedEfloat": 900, "projectedCash": 700,
            "hoursToEfloatStockout": 18, "hoursToCashStockout": None,
            "riskType": "NONE", "recommendedEfloatTopup": 0, "recommendedCashTopup": 0,
        })
    return httpx.Response(404, json={"message": "not found"})


@pytest.fixture
def assistant_app(tmp_path):
    settings = Settings(
        environment="test",
        internal_api_key="test-internal-key",
        database_url=f"sqlite:///{tmp_path / 'gov.db'}",
        backend_base_url="http://backend.test",
        llm_provider="none",
    )
    app = create_app(settings)
    with TestClient(app) as client:
        yield client, settings, app


def test_deterministic_assistant_balance(assistant_app):
    client, settings, app = assistant_app
    transport = httpx.MockTransport(_backend_transport)
    http = httpx.Client(transport=transport)
    backend = BackendClient(settings, "Bearer user-jwt", client=http)

    request = client.app.router  # noqa: F841 — ensure app loaded
    # Call the service directly with the mocked backend.
    body = AssistantChatRequest(
        role="AGENT", agent_id="a1", username="agent1",
        question="What is my balance and forecast?",
        authorization="Bearer user-jwt",
    )
    # Build a minimal request-like object with app.state
    class Req:
        pass
    req = Req()
    req.app = app

    # Lifespan already ran via TestClient; state is set.
    response = handle_chat(req, body, backend=backend)
    assert response.refused is False
    assert response.engine == "DETERMINISTIC_INTENT_ROUTER"
    assert response.tools_used
    assert "VERIFIED_FACT" in response.data_classifications or any(
        t.classification == "VERIFIED_FACT" for t in response.tools_used)
    assert "1200.50" in response.answer or "efloat" in response.answer.lower()
    http.close()


def test_assistant_http_refusal(assistant_app):
    client, settings, _app = assistant_app
    response = client.post("/internal/ai/v1/assistant/chat", headers={
        "X-Internal-Api-Key": settings.internal_api_key,
    }, json={
        "role": "AGENT",
        "question": "Please transfer cash to agent AG002",
        "authorization": "Bearer user-jwt",
    })
    assert response.status_code == 200
    body = response.json()
    assert body["refused"] is True
    assert body["engine"] == "GUARDRAIL"


def test_llm_tool_loop(assistant_app):
    client, settings, app = assistant_app
    settings.llm_provider = "openai"
    settings.llm_api_key = "sk-test"
    app.state.settings = settings

    round_state = {"n": 0}

    def llm_and_backend(request: httpx.Request) -> httpx.Response:
        if request.url.host == "api.openai.com":
            round_state["n"] += 1
            if round_state["n"] == 1:
                return httpx.Response(200, json={
                    "choices": [{
                        "message": {
                            "role": "assistant",
                            "content": None,
                            "tool_calls": [{
                                "id": "call_1",
                                "type": "function",
                                "function": {"name": "get_my_balances", "arguments": "{}"},
                            }],
                        }
                    }]
                })
            return httpx.Response(200, json={
                "choices": [{
                    "message": {
                        "role": "assistant",
                        "content": "Your e-float is 1200.50 (VERIFIED_FACT).",
                    }
                }]
            })
        return _backend_transport(request)

    transport = httpx.MockTransport(llm_and_backend)
    http = httpx.Client(transport=transport)
    backend = BackendClient(settings, "Bearer user-jwt", client=http)

    class Req:
        pass
    req = Req()
    req.app = app
    body = AssistantChatRequest(
        role="AGENT", agent_id="a1", question="How much e-float do I have?",
        authorization="Bearer user-jwt")
    response = handle_chat(req, body, backend=backend, llm_http=http)
    assert response.engine.startswith("LLM:")
    assert any(t.name == "get_my_balances" for t in response.tools_used)
    assert "1200.50" in response.answer
    http.close()
