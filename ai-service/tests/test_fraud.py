def _payload(**overrides):
    base = {
        "agent_id": "a1",
        "agent_code": "AG001",
        "amount": 500,
        "agent_history_count_30d": 40,
        "agent_avg_amount_30d": 100,
        "agent_std_amount_30d": 20,
        "amount_z_score": 0.5,
        "amount_to_avg_ratio": 5.0,
        "agent_tx_count_1h": 1,
        "agent_tx_count_24h": 3,
        "agent_latitude": -17.8,
        "agent_longitude": 31.0,
        "transaction_latitude": -17.8,
        "transaction_longitude": 31.0,
    }
    base.update(overrides)
    return base


def test_low_risk_clean_transaction(client, auth_headers):
    response = client.post("/internal/ai/v1/fraud/evaluate", json=_payload(), headers=auth_headers)
    assert response.status_code == 200
    body = response.json()
    assert body["risk_category"] == "LOW"
    assert body["fraud_probability"] == body["risk_score"] / 100
    assert body["governance"]["is_trained_model"] is False
    assert body["governance"]["data_classification"] == "PREDICTION"
    assert "prediction_id" in body


def test_far_location_and_extreme_amount(client, auth_headers):
    response = client.post("/internal/ai/v1/fraud/evaluate", json=_payload(
        transaction_latitude=-18.5,  # ~78 km south
        amount_z_score=4.5,
        amount_to_avg_ratio=10,
        agent_tx_count_1h=9,
        confirmed_fraud_count=1,
    ), headers=auth_headers)
    body = response.json()
    assert body["risk_score"] >= 60
    assert body["risk_category"] in {"HIGH", "CRITICAL"}
    assert "LOCATION_FAR_FROM_REGISTERED" in body["reason_codes"]
    assert body["recommended_action"] in {"REVIEW_TRANSACTION", "OPEN_INVESTIGATION"}
