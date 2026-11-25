"""Float redistribution as a linear programme.

Variables: peer swaps x[a,b] (agent a sends e-float to b and receives the same amount of cash), area-pool
e-float/cash top-ups, and shortfall slacks. Objective: minimise urgency-weighted shortfall, then travel distance,
then pool usage. A swap only happens when it helps both agents (a needs cash, b needs e-float).
"""
from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Any

import numpy as np
from scipy.optimize import linprog

from app.common.geo import haversine_km
from app.common.stats import round_down_to, round_money
from app.optimisation.schemas import AgentPlan, OptimisationRequest, PeerExchange, PlanTotals, PoolReplenishment

STEP = 10.0


def urgency(hours: float | None) -> float:
    if hours is None:
        return 1.0
    return 1.0 + min(4.0, 24.0 / max(1.0, hours))


@dataclass
class OptimisationResult:
    exchanges: list[PeerExchange]
    replenishments: list[PoolReplenishment]
    agents: list[AgentPlan]
    totals: PlanTotals
    solver: dict[str, Any]
    explanation: str
    confidence: float
    confidence_basis: str


def optimise(req: OptimisationRequest) -> OptimisationResult:
    agents = req.agents
    n = len(agents)
    proj_e = [a.efloat + a.expected_efloat_net_flow for a in agents]
    proj_c = [a.cash + a.expected_cash_net_flow for a in agents]
    target_e = [a.min_efloat * req.safety_buffer for a in agents]
    target_c = [a.min_cash * req.safety_buffer for a in agents]
    need_e = [max(0.0, target_e[i] - proj_e[i]) if agents[i].active else 0.0 for i in range(n)]
    need_c = [max(0.0, target_c[i] - proj_c[i]) if agents[i].active else 0.0 for i in range(n)]
    surplus_e = [max(0.0, min(proj_e[i], agents[i].efloat) - target_e[i]) if agents[i].active else 0.0 for i in range(n)]
    surplus_c = [max(0.0, min(proj_c[i], agents[i].cash) - target_c[i]) if agents[i].active else 0.0 for i in range(n)]

    dist: dict[tuple[int, int], float] = {}
    for a in range(n):
        for b in range(n):
            if a == b or surplus_e[a] <= 0 or need_c[a] <= 0 or need_e[b] <= 0 or surplus_c[b] <= 0:
                continue
            pa, pb = agents[a], agents[b]
            if None in (pa.latitude, pa.longitude, pb.latitude, pb.longitude):
                continue
            d = haversine_km(pa.latitude, pa.longitude, pb.latitude, pb.longitude)  # type: ignore[arg-type]
            if d <= req.max_peer_distance_km:
                dist[(a, b)] = d

    pairs = list(dist)
    pe_idx = [i for i in range(n) if need_e[i] > 0]
    pc_idx = [i for i in range(n) if need_c[i] > 0]
    nv = len(pairs) + 2 * len(pe_idx) + 2 * len(pc_idx)
    x_off, pe_off = 0, len(pairs)
    pc_off = pe_off + len(pe_idx)
    se_off = pc_off + len(pc_idx)
    sc_off = se_off + len(pe_idx)

    c = np.zeros(nv)
    for k, (a, b) in enumerate(pairs):
        c[x_off + k] = req.peer_cost_per_km * dist[(a, b)]
    c[pe_off:pe_off + len(pe_idx)] = req.pool_cost
    c[pc_off:pc_off + len(pc_idx)] = req.pool_cost
    for j, i in enumerate(pe_idx):
        c[se_off + j] = urgency(agents[i].hours_to_efloat_stockout)
    for j, i in enumerate(pc_idx):
        c[sc_off + j] = urgency(agents[i].hours_to_cash_stockout)

    rows: list[np.ndarray] = []
    rhs: list[float] = []

    def row() -> np.ndarray:
        return np.zeros(nv)

    for a in range(n):
        ks = [k for k, (pa, _) in enumerate(pairs) if pa == a]
        if ks:
            r = row()
            r[[x_off + k for k in ks]] = 1
            rows.append(r)
            rhs.append(surplus_e[a])
    for b in range(n):
        ks = [k for k, (_, pb) in enumerate(pairs) if pb == b]
        if ks:
            r = row()
            r[[x_off + k for k in ks]] = 1
            rows.append(r)
            rhs.append(surplus_c[b])
    for j, b in enumerate(pe_idx):
        ks = [k for k, (_, pb) in enumerate(pairs) if pb == b]
        cover = row()
        cover[[x_off + k for k in ks]] = 1
        cover[pe_off + j] = 1
        rows.append(cover.copy())
        rhs.append(need_e[b])
        cover[se_off + j] = 1
        rows.append(-cover)
        rhs.append(-need_e[b])
    for j, a in enumerate(pc_idx):
        ks = [k for k, (pa, _) in enumerate(pairs) if pa == a]
        cover = row()
        cover[[x_off + k for k in ks]] = 1
        cover[pc_off + j] = 1
        rows.append(cover.copy())
        rhs.append(need_c[a])
        cover[sc_off + j] = 1
        rows.append(-cover)
        rhs.append(-need_c[a])
    if pe_idx:
        r = row()
        r[pe_off:pe_off + len(pe_idx)] = 1
        rows.append(r)
        rhs.append(req.pool_efloat)
    if pc_idx:
        r = row()
        r[pc_off:pc_off + len(pc_idx)] = 1
        rows.append(r)
        rhs.append(req.pool_cash)

    solution = np.zeros(nv)
    status = "NOTHING_TO_OPTIMISE"
    objective = 0.0
    if nv > 0:
        res = linprog(c, A_ub=np.vstack(rows) if rows else None, b_ub=np.asarray(rhs) if rows else None,
                      bounds=[(0, None)] * nv, method="highs")
        status = "OPTIMAL" if res.status == 0 else f"SOLVER_STATUS_{res.status}"
        if res.status == 0:
            solution = res.x
            objective = float(res.fun)

    received_e = [0.0] * n
    received_c = [0.0] * n
    sent_e = [0.0] * n
    sent_c = [0.0] * n
    exchanges: list[PeerExchange] = []
    for k, (a, b) in enumerate(pairs):
        amount = round_down_to(solution[x_off + k], STEP)
        if amount < max(req.min_transfer, STEP):
            continue
        sent_e[a] += amount
        received_e[b] += amount
        sent_c[b] += amount
        received_c[a] += amount
        pa, pb = agents[a], agents[b]
        exchanges.append(PeerExchange(
            efloat_provider_agent_id=pa.agent_id, efloat_provider_agent_code=pa.agent_code, cash_provider_agent_id=pb.agent_id,
            cash_provider_agent_code=pb.agent_code, amount=amount, distance_km=round(dist[(a, b)], 2),
            rationale=f"{pa.agent_code} has spare e-float and needs cash; {pb.agent_code} has spare cash and needs e-float. "
                      f"They are {dist[(a, b)]:.1f} km apart, so a swap avoids drawing on the area pool."))

    replenishments: list[PoolReplenishment] = []
    pool_e = pool_c = 0.0
    for j, i in enumerate(pe_idx):
        amount = round_down_to(solution[pe_off + j], STEP)
        if amount >= max(req.min_transfer, STEP):
            pool_e += amount
            received_e[i] += amount
            u = urgency(agents[i].hours_to_efloat_stockout)
            replenishments.append(PoolReplenishment(
                agent_id=agents[i].agent_id, agent_code=agents[i].agent_code, type="EFLOAT", amount=amount, urgency=round(u, 2),
                rationale=_pool_reason(agents[i].agent_code, "e-float", need_e[i], agents[i].hours_to_efloat_stockout)))
    for j, i in enumerate(pc_idx):
        amount = round_down_to(solution[pc_off + j], STEP)
        if amount >= max(req.min_transfer, STEP):
            pool_c += amount
            received_c[i] += amount
            u = urgency(agents[i].hours_to_cash_stockout)
            replenishments.append(PoolReplenishment(
                agent_id=agents[i].agent_id, agent_code=agents[i].agent_code, type="CASH", amount=amount, urgency=round(u, 2),
                rationale=_pool_reason(agents[i].agent_code, "cash", need_c[i], agents[i].hours_to_cash_stockout)))
    replenishments.sort(key=lambda r: r.urgency, reverse=True)

    plans = []
    for i, a in enumerate(agents):
        plans.append(AgentPlan(
            agent_id=a.agent_id, agent_code=a.agent_code, projected_efloat=round_money(proj_e[i]),
            projected_cash=round_money(proj_c[i]), target_efloat=round_money(target_e[i]), target_cash=round_money(target_c[i]),
            efloat_need=round_money(need_e[i]), cash_need=round_money(need_c[i]), efloat_surplus=round_money(surplus_e[i]),
            cash_surplus=round_money(surplus_c[i]), efloat_after_plan=round_money(proj_e[i] + received_e[i] - sent_e[i]),
            cash_after_plan=round_money(proj_c[i] + received_c[i] - sent_c[i]),
            unmet_efloat=round_money(max(0.0, need_e[i] - received_e[i])),
            unmet_cash=round_money(max(0.0, need_c[i] - received_c[i]))))

    peers_e = sum(e.amount for e in exchanges)
    totals = PlanTotals(
        efloat_need=round_money(sum(need_e)), cash_need=round_money(sum(need_c)), efloat_from_peers=peers_e,
        cash_from_peers=peers_e, efloat_from_pool=pool_e, cash_from_pool=pool_c,
        unmet_efloat=round_money(sum(p.unmet_efloat for p in plans)), unmet_cash=round_money(sum(p.unmet_cash for p in plans)),
        pool_efloat_remaining=round_money(req.pool_efloat - pool_e), pool_cash_remaining=round_money(req.pool_cash - pool_c),
        peer_distance_km=round(sum(e.distance_km for e in exchanges), 2))

    explanation = (f"Projected needs over {req.horizon_hours}h (target = minimum level x {req.safety_buffer}): e-float "
                   f"{totals.efloat_need:,.0f}, cash {totals.cash_need:,.0f}. Plan: {len(exchanges)} peer exchange(s) moving "
                   f"{peers_e:,.0f}, and pool top-ups of {pool_e:,.0f} e-float and {pool_c:,.0f} cash.")
    if totals.unmet_efloat > 0 or totals.unmet_cash > 0:
        explanation += (f" Unmet after the plan: e-float {totals.unmet_efloat:,.0f}, cash {totals.unmet_cash:,.0f} "
                        "(pool limits or no suitable nearby peer).")
    if not exchanges and not replenishments:
        explanation += " No redistribution is needed."

    with_location = sum(1 for a in agents if a.latitude is not None and a.longitude is not None) / n
    forecast_conf = [a.forecast_confidence if a.forecast_confidence is not None else 0.5 for a in agents]
    confidence = float(np.mean(forecast_conf)) * (0.7 + 0.3 * with_location)
    solver = {"method": "scipy.optimize.linprog (HiGHS)", "status": status, "objective": round(objective, 4),
              "variables": nv, "constraints": len(rows), "candidate_peer_pairs": len(pairs)}
    return OptimisationResult(exchanges=exchanges, replenishments=replenishments, agents=plans, totals=totals,
                              solver=solver, explanation=explanation, confidence=round(confidence, 4),
                              confidence_basis="Mean demand-forecast confidence, reduced when agent locations are missing")


def _pool_reason(code: str, what: str, need: float, hours: float | None) -> str:
    when = "" if hours is None else (" and is already below its minimum" if hours == 0
                                     else f" and is projected to fall below its minimum in about {math.ceil(hours)}h")
    return f"{code} needs about {need:,.0f} {what} to stay above its target level{when}; no nearby peer can cover it."
