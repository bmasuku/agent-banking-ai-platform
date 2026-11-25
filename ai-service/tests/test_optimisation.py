def test_peer_swap_and_pool_topup(client, auth_headers):
    payload = {
        "area_id": "area-1",
        "area_code": "HRE-CBD",
        "pool_efloat": 5000,
        "pool_cash": 2000,
        "max_peer_distance_km": 20,
        "agents": [
            {
                "agent_id": "a1", "agent_code": "AG001",
                "latitude": -17.8, "longitude": 31.05,
                "efloat": 3000, "cash": 100,
                "min_efloat": 500, "min_cash": 800,
                "expected_efloat_net_flow": -200, "expected_cash_net_flow": -100,
                "hours_to_cash_stockout": 2,
            },
            {
                "agent_id": "a2", "agent_code": "AG002",
                "latitude": -17.81, "longitude": 31.06,
                "efloat": 200, "cash": 2500,
                "min_efloat": 800, "min_cash": 400,
                "expected_efloat_net_flow": -150, "expected_cash_net_flow": 50,
                "hours_to_efloat_stockout": 3,
            },
            {
                "agent_id": "a3", "agent_code": "AG003",
                "latitude": -17.9, "longitude": 31.2,
                "efloat": 100, "cash": 100,
                "min_efloat": 700, "min_cash": 700,
                "expected_efloat_net_flow": -50, "expected_cash_net_flow": -50,
                "hours_to_efloat_stockout": 1, "hours_to_cash_stockout": 1,
            },
        ],
    }
    body = client.post("/internal/ai/v1/float/optimise", json=payload, headers=auth_headers).json()
    assert body["solver"]["status"] == "OPTIMAL"
    assert body["peer_exchanges"] or body["pool_replenishments"]
    assert "execution_note" in body
    assert body["governance"]["method"] == "LINEAR_PROGRAMMING"
