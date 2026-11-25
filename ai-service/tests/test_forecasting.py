from datetime import UTC, datetime, timedelta


def _history(days: int = 14):
    start = datetime(2024, 1, 1, tzinfo=UTC)
    rows = []
    for d in range(days):
        for h in range(24):
            peak = 1.5 if 10 <= h <= 14 else 0.5
            rows.append({
                "hour": (start + timedelta(days=d, hours=h)).isoformat(),
                "efloat_net": -30 * peak,
                "cash_net": -20 * peak,
                "transaction_count": int(4 * peak),
            })
    return rows


def test_agent_forecast(client, auth_headers):
    payload = {
        "agent_id": "a1",
        "agent_code": "AG001",
        "current_efloat": 2000,
        "current_cash": 1500,
        "min_efloat_threshold": 400,
        "min_cash_threshold": 300,
        "horizon_hours": 24,
        "as_of": "2024-01-15T08:00:00Z",
        "timezone": "UTC",
        "history": _history(),
    }
    body = client.post("/internal/ai/v1/float/forecast", json=payload, headers=auth_headers).json()
    assert len(body["trajectory"]) == 24
    assert body["predicted_efloat_net_flow"] < 0
    assert body["metrics"]["observed_days"] >= 10
    assert 0 < body["confidence"] <= 1
    assert body["governance"]["method"] == "STATISTICAL"


def test_area_forecast(client, auth_headers):
    agent = {
        "agent_id": "a1",
        "agent_code": "AG001",
        "current_efloat": 2000,
        "current_cash": 1500,
        "min_efloat_threshold": 400,
        "min_cash_threshold": 300,
        "horizon_hours": 24,
        "as_of": "2024-01-15T08:00:00Z",
        "timezone": "UTC",
        "history": _history(10),
    }
    payload = {
        "area_id": "area-1",
        "area_code": "HRE-CBD",
        "pool_efloat": 10000,
        "pool_cash": 5000,
        "agents": [agent],
    }
    body = client.post("/internal/ai/v1/float/forecast/area", json=payload, headers=auth_headers).json()
    assert body["totals"]["agents"] == 1
    assert len(body["agents"]) == 1
