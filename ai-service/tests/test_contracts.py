"""Field-name contracts expected by the Spring Boot AiServiceClient (Jackson SNAKE_CASE)."""


def test_fraud_response_has_backend_fields(client, auth_headers):
    body = client.post("/internal/ai/v1/fraud/evaluate", json={
        "amount": 50, "agent_history_count_30d": 15,
    }, headers=auth_headers).json()
    for key in ("fraud_probability", "reason_codes", "feature_contributions", "explanation", "model_version"):
        assert key in body


def test_credit_response_has_backend_fields(client, auth_headers):
    body = client.post("/internal/ai/v1/credit/score", json={
        "tenure_days": 100, "transaction_volume_30d": 10000, "avg_daily_commission_30d": 5,
        "active_days_30d": 20, "advances_repaid": 1,
    }, headers=auth_headers).json()
    for key in ("credit_score", "probability_of_default", "recommended_limit", "factors",
                "explanation", "model_version"):
        assert key in body


def test_forecast_response_has_backend_fields(client, auth_headers):
    body = client.post("/internal/ai/v1/float/forecast", json={
        "current_efloat": 1000, "current_cash": 1000, "as_of": "2024-01-10T10:00:00Z",
        "timezone": "UTC", "history": [],
    }, headers=auth_headers).json()
    for key in ("projected_efloat", "projected_cash", "min_projected_efloat", "min_projected_cash",
                "predicted_efloat_net_flow", "predicted_cash_net_flow", "hours_to_efloat_stockout",
                "hours_to_cash_stockout", "confidence", "trajectory", "drivers", "explanation",
                "model_version"):
        assert key in body


def test_jackson_digit_alias_accepted():
    from app.fraud.schemas import FraudRequest
    # Jackson SNAKE_CASE turns agentTxCount1h into agent_tx_count1h (no underscore before digit).
    req = FraudRequest.model_validate({"amount": 10, "agent_tx_count1h": 7, "agent_history_count30d": 20})
    assert req.agent_tx_count_1h == 7
    assert req.agent_history_count_30d == 20
