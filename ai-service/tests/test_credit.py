def _payload(**overrides):
    base = {
        "agent_id": "a1",
        "agent_code": "AG001",
        "tenure_days": 400,
        "active_days_30d": 25,
        "transaction_count_30d": 200,
        "transaction_volume_30d": 50000,
        "avg_daily_commission_30d": 12,
        "commission_volatility": 0.4,
        "outstanding_principal": 0,
        "outstanding_interest": 0,
        "advances_repaid": 3,
        "advances_repaid_late": 0,
        "advances_overdue": 0,
        "advances_defaulted": 0,
        "flagged_transactions_30d": 0,
        "confirmed_fraud_count": 0,
        "open_investigations": 0,
        "agent_status": "ACTIVE",
        "requested_amount": 500,
    }
    base.update(overrides)
    return base


def test_strong_agent_approved(client, auth_headers):
    body = client.post("/internal/ai/v1/credit/score", json=_payload(), headers=auth_headers).json()
    assert 0 <= body["credit_score"] <= 1000
    assert body["credit_score"] >= 720
    assert body["decision"] == "APPROVED"
    assert body["eligible"] is True
    assert body["governance"]["is_trained_model"] is False


def test_defaulted_rejected(client, auth_headers):
    body = client.post("/internal/ai/v1/credit/score", json=_payload(
        advances_defaulted=1, advances_overdue=1, confirmed_fraud_count=1,
    ), headers=auth_headers).json()
    assert body["decision"] == "REJECTED"
    assert body["eligible"] is False
    assert body["risk_category"] in {"HIGH", "CRITICAL"}
