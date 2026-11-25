def test_prediction_persisted_and_decision_recorded(client, auth_headers):
    scored = client.post("/internal/ai/v1/fraud/evaluate", json={
        "amount": 100, "agent_id": "a1", "agent_code": "AG001",
        "agent_history_count_30d": 20, "amount_z_score": 0.1,
    }, headers=auth_headers).json()
    prediction_id = scored["prediction_id"]

    listed = client.get("/internal/ai/v1/governance/predictions", headers=auth_headers).json()
    assert listed["total"] >= 1
    assert any(item["id"] == prediction_id for item in listed["items"])

    detail = client.get(f"/internal/ai/v1/governance/predictions/{prediction_id}", headers=auth_headers).json()
    assert detail["kind"] == "FRAUD"
    assert detail["data_classification"] == "PREDICTION"

    decision = client.post(f"/internal/ai/v1/governance/predictions/{prediction_id}/decisions", json={
        "decision": "CLEARED",
        "decided_by": "risk.officer",
        "decided_by_role": "RISK_OFFICER",
        "notes": "False positive",
        "source_reference": "INV-1",
    }, headers=auth_headers)
    assert decision.status_code == 201
    assert decision.json()["agrees_with_model"] is not None or decision.json()["decision"] == "CLEARED"

    models = client.get("/internal/ai/v1/governance/models", headers=auth_headers).json()
    assert len(models) >= 5

    summary = client.get("/internal/ai/v1/governance/summary", headers=auth_headers).json()
    assert "models" in summary
