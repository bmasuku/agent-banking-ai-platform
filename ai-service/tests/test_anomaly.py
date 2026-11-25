from datetime import UTC, datetime, timedelta


def test_detect_unusual_amount(client, auth_headers):
    as_of = datetime(2024, 2, 1, 12, tzinfo=UTC)
    txs = []
    for i in range(80):
        txs.append({
            "id": f"b{i}",
            "timestamp": (as_of - timedelta(days=10, hours=i % 24)).isoformat(),
            "type": "CASH_WITHDRAWAL",
            "amount": 100 + (i % 5) * 5,
            "status": "COMPLETED",
        })
    txs.append({
        "id": "spike",
        "timestamp": (as_of - timedelta(minutes=20)).isoformat(),
        "type": "CASH_WITHDRAWAL",
        "amount": 5000,
        "status": "COMPLETED",
    })
    body = client.post("/internal/ai/v1/anomaly/detect", json={
        "agent_id": "a1",
        "agent_code": "AG001",
        "as_of": as_of.isoformat(),
        "timezone": "UTC",
        "recent_window_hours": 24,
        "transactions": txs,
    }, headers=auth_headers).json()
    assert body["baseline_transactions"] >= 50
    assert body["anomalies"]
    assert body["governance"]["method"] == "STATISTICAL_AND_ISOLATION_FOREST"


def test_peer_comparison(client, auth_headers):
    peers = []
    for i in range(12):
        peers.append({
            "agent_id": f"a{i}",
            "agent_code": f"AG{i:03d}",
            "features": {
                "transactions_7d": 40 + i,
                "volume_7d": 10000 + i * 100,
                "avg_amount_7d": 200,
                "off_hours_share_7d": 0.1,
                "withdrawal_share_7d": 0.5,
                "flagged_7d": 0,
                "reversals_7d": 0,
                "distinct_customers_7d": 30,
            },
        })
    peers[0]["features"]["volume_7d"] = 200000
    peers[0]["features"]["flagged_7d"] = 8
    body = client.post("/internal/ai/v1/anomaly/peer-comparison", json={
        "area_id": "area-1",
        "area_code": "HRE",
        "agents": peers,
    }, headers=auth_headers).json()
    assert any(a["abnormal"] for a in body["agents"])
