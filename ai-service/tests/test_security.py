def test_health_is_public(client):
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "UP"
    assert body["llm_enabled"] is False
    assert any(m["name"] == "fraud-risk-rules" for m in body["models"])


def test_internal_endpoint_requires_key(client):
    response = client.post("/internal/ai/v1/fraud/evaluate", json={"amount": 100})
    assert response.status_code == 401
    assert response.json()["error_code"] == "INVALID_INTERNAL_API_KEY"


def test_wrong_key_rejected(client):
    response = client.post(
        "/internal/ai/v1/fraud/evaluate",
        json={"amount": 100, "agent_history_count_30d": 20},
        headers={"X-Internal-Api-Key": "wrong"},
    )
    assert response.status_code == 401
