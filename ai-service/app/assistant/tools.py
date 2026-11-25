"""Role-scoped, GET-only tool registry. Tools never mutate platform state."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Callable, Literal

from app.assistant.backend_client import BackendClient, BackendError

Classification = Literal["VERIFIED_FACT", "PREDICTION"]


@dataclass(frozen=True)
class ToolSpec:
    name: str
    description: str
    parameters: dict[str, Any]
    roles: frozenset[str]
    classification: Classification
    path_template: str
    # Optional static query params merged with caller args (after reserved keys are removed).
    static_params: dict[str, Any] = field(default_factory=dict)
    # Argument names that belong in the path template, not the query string.
    path_args: frozenset[str] = field(default_factory=frozenset)
    # When True, the tool needs no path-arg substitution beyond the template itself.
    summarise: Callable[[Any], str] | None = None


def _money(v: Any) -> str:
    try:
        return f"{float(v):,.2f}"
    except (TypeError, ValueError):
        return str(v)


def _balances_summary(data: Any) -> str:
    if not isinstance(data, dict):
        return str(data)[:400]
    bals = data.get("balances") or data
    return (f"Agent {data.get('agentCode') or data.get('agent_code') or ''}: "
            f"e-float {_money(bals.get('efloat'))}, cash {_money(bals.get('cash'))}, "
            f"commission payable {_money(bals.get('commissionPayable') or bals.get('commission_payable'))}, "
            f"advance outstanding {_money(bals.get('advanceOutstanding') or bals.get('advance_outstanding'))}. "
            f"efloatLow={data.get('efloatLow') or data.get('efloat_low')}, cashLow={data.get('cashLow') or data.get('cash_low')}.")


def _page_summary(label: str, fields: list[str]) -> Callable[[Any], str]:
    def _fn(data: Any) -> str:
        rows = data.get("content") if isinstance(data, dict) else data
        if not isinstance(rows, list):
            return str(data)[:400]
        total = data.get("totalElements", len(rows)) if isinstance(data, dict) else len(rows)
        lines = []
        for row in rows[:5]:
            if isinstance(row, dict):
                bits = [f"{f}={row.get(f)}" for f in fields if row.get(f) is not None]
                lines.append(", ".join(bits) or str(row)[:120])
        more = f" (showing {len(lines)} of {total})" if total else ""
        return f"{label}{more}: " + ("; ".join(lines) if lines else "none")
    return _fn


def _prediction_summary(data: Any) -> str:
    if isinstance(data, list):
        return "; ".join(_prediction_summary(item) for item in data[:5]) or "no predictions"
    if not isinstance(data, dict):
        return str(data)[:400]
    return (f"Agent {data.get('agentCode')}: projected e-float {_money(data.get('projectedEfloat'))}, "
            f"cash {_money(data.get('projectedCash'))}; hours to e-float stockout={data.get('hoursToEfloatStockout')}, "
            f"cash={data.get('hoursToCashStockout')}; risk={data.get('riskType')}; "
            f"recommended top-up e-float={_money(data.get('recommendedEfloatTopup'))}, "
            f"cash={_money(data.get('recommendedCashTopup'))}. PREDICTION, not a ledger balance.")


def _credit_summary(data: Any) -> str:
    if not isinstance(data, dict):
        return str(data)[:400]
    return (f"Credit score {data.get('score')} ({data.get('riskBand')}); "
            f"recommended limit {_money(data.get('recommendedLimit'))}; "
            f"PD={data.get('probabilityOfDefault')}; source={data.get('modelSource')}. "
            f"PREDICTION — not a verified fact.")


def _area_summary(data: Any) -> str:
    if not isinstance(data, dict):
        return str(data)[:400]
    return (f"Area {data.get('code') or data.get('areaCode')}: e-float pool {_money(data.get('efloatPoolBalance') or data.get('efloatPool'))}, "
            f"cash pool {_money(data.get('cashPoolBalance') or data.get('cashPool'))}, "
            f"agents={data.get('agentCount')}.")


def _area_report_summary(data: Any) -> str:
    if not isinstance(data, dict):
        return str(data)[:400]
    return (f"Area report {data.get('areaCode')}: pool e-float {_money(data.get('efloatPool'))}, "
            f"cash {_money(data.get('cashPool'))}; low e-float agents={data.get('agentsLowOnEfloat')}, "
            f"low cash={data.get('agentsLowOnCash')}; flagged={data.get('flaggedTransactions')}.")


def _risk_dashboard_summary(data: Any) -> str:
    if not isinstance(data, dict):
        return str(data)[:400]
    return (f"Risk dashboard: pending reviews={data.get('pendingReviews')}, "
            f"open investigations={data.get('openInvestigations')}, "
            f"flagged last 24h={data.get('flaggedLast24h')}, blocked last 24h={data.get('blockedLast24h')}.")


def _float_position_summary(data: Any) -> str:
    if not isinstance(data, dict):
        return str(data)[:400]
    return (f"Float position: settlement bank {_money(data.get('settlementBank'))}, "
            f"agent e-float {_money(data.get('totalAgentEfloat'))}, agent cash {_money(data.get('totalAgentCash'))}, "
            f"area e-float pools {_money(data.get('totalAreaEfloatPools'))}, "
            f"advance receivable {_money(data.get('totalAdvanceReceivable'))}.")


def _trial_balance_summary(data: Any) -> str:
    if not isinstance(data, dict):
        return str(data)[:400]
    return (f"Trial balance as of {data.get('asOf')}: total debit {_money(data.get('totalDebit'))}, "
            f"credit {_money(data.get('totalCredit'))}, balanced={data.get('balanced')}.")


def _income_summary(data: Any) -> str:
    if not isinstance(data, dict):
        return str(data)[:400]
    return (f"Income statement: fee {_money(data.get('feeIncome'))}, interest {_money(data.get('interestIncome'))}, "
            f"commission expense {_money(data.get('commissionExpense'))}, net {_money(data.get('netIncome'))}.")


def _commission_summary(data: Any) -> str:
    if not isinstance(data, dict):
        return str(data)[:400]
    return (f"Commission for {data.get('agentCode')}: accrued payable {_money(data.get('accruedPayable'))} "
            f"({data.get('accruedCount')} entries); lifetime net disbursed {_money(data.get('lifetimeNetDisbursed'))}.")


def _me_summary(data: Any) -> str:
    if not isinstance(data, dict):
        return str(data)[:400]
    return (f"Signed in as {data.get('username') or data.get('fullName')} "
            f"(role={data.get('role')}, agentCode={data.get('agentCode')}, areaCode={data.get('areaCode')}).")


TOOLS: list[ToolSpec] = [
    ToolSpec(
        "get_my_profile", "Current authenticated user / agent profile",
        {"type": "object", "properties": {}},
        frozenset({"AGENT", "AREA_MANAGER", "RISK_OFFICER", "FINANCE_OFFICER", "ADMIN"}),
        "VERIFIED_FACT", "/api/v1/auth/me", summarise=_me_summary),
    ToolSpec(
        "get_my_balances", "Agent's current e-float, cash, commission and advance balances",
        {"type": "object", "properties": {}},
        frozenset({"AGENT"}), "VERIFIED_FACT", "/api/v1/agent/balances", summarise=_balances_summary),
    ToolSpec(
        "get_my_transactions", "Recent transactions for the signed-in agent",
        {"type": "object", "properties": {
            "page": {"type": "integer", "default": 0},
            "size": {"type": "integer", "default": 10},
        }},
        frozenset({"AGENT"}), "VERIFIED_FACT", "/api/v1/agent/transactions",
        summarise=_page_summary("Transactions", ["reference", "type", "status", "amount", "createdAt"])),
    ToolSpec(
        "get_my_advances", "Float advances for the signed-in agent",
        {"type": "object", "properties": {
            "status": {"type": "string"},
            "page": {"type": "integer", "default": 0},
            "size": {"type": "integer", "default": 10},
        }},
        frozenset({"AGENT"}), "VERIFIED_FACT", "/api/v1/agent/advances",
        summarise=_page_summary("Advances", ["reference", "status", "requestedAmount", "totalOutstanding"])),
    ToolSpec(
        "get_my_commissions", "Commission summary for the signed-in agent",
        {"type": "object", "properties": {}},
        frozenset({"AGENT"}), "VERIFIED_FACT", "/api/v1/agent/commissions/summary", summarise=_commission_summary),
    ToolSpec(
        "get_my_exchanges", "Peer float exchanges for the signed-in agent",
        {"type": "object", "properties": {
            "page": {"type": "integer", "default": 0},
            "size": {"type": "integer", "default": 10},
        }},
        frozenset({"AGENT"}), "VERIFIED_FACT", "/api/v1/agent/exchanges",
        summarise=_page_summary("Exchanges", ["reference", "status", "amount", "direction"])),
    ToolSpec(
        "get_my_float_forecast", "Latest float demand prediction for the signed-in agent (PREDICTION)",
        {"type": "object", "properties": {}},
        frozenset({"AGENT"}), "PREDICTION", "/api/v1/predictions/agents/{agent_id}",
        path_args=frozenset({"agent_id"}), summarise=_prediction_summary),
    ToolSpec(
        "get_area_overview", "Area manager's area profile and pool balances",
        {"type": "object", "properties": {}},
        frozenset({"AREA_MANAGER"}), "VERIFIED_FACT", "/api/v1/area-manager/area", summarise=_area_summary),
    ToolSpec(
        "get_area_agents", "Agents in the area manager's area with balances and risk",
        {"type": "object", "properties": {}},
        frozenset({"AREA_MANAGER"}), "VERIFIED_FACT", "/api/v1/area-manager/agents",
        summarise=lambda data: _page_summary(
            "Area agents", ["agentCode", "businessName", "efloat", "cash", "efloatLow", "cashLow", "riskLevel"]
        )({"content": data, "totalElements": len(data)} if isinstance(data, list) else data)),
    ToolSpec(
        "get_area_agent_balances", "Balances for one agent in the area manager's area",
        {"type": "object", "properties": {"agent_id": {"type": "string"}}, "required": ["agent_id"]},
        frozenset({"AREA_MANAGER"}), "VERIFIED_FACT", "/api/v1/area-manager/agents/{agent_id}/balances",
        path_args=frozenset({"agent_id"}), summarise=_balances_summary),
    ToolSpec(
        "get_area_predictions", "Float predictions for all agents in the area (PREDICTION)",
        {"type": "object", "properties": {}},
        frozenset({"AREA_MANAGER"}), "PREDICTION", "/api/v1/area-manager/predictions", summarise=_prediction_summary),
    ToolSpec(
        "get_area_alerts", "Fraud alerts for agents in the area",
        {"type": "object", "properties": {
            "reviewStatus": {"type": "string"},
            "page": {"type": "integer", "default": 0},
            "size": {"type": "integer", "default": 10},
        }},
        frozenset({"AREA_MANAGER"}), "VERIFIED_FACT", "/api/v1/area-manager/alerts",
        summarise=_page_summary("Alerts", ["reference", "agentCode", "amount", "riskLevel", "reviewStatus"])),
    ToolSpec(
        "get_area_report", "Area operational report (balances, transactions, alerts)",
        {"type": "object", "properties": {}},
        frozenset({"AREA_MANAGER"}), "VERIFIED_FACT", "/api/v1/area-manager/reports/area", summarise=_area_report_summary),
    ToolSpec(
        "get_risk_dashboard", "Risk officer dashboard summary",
        {"type": "object", "properties": {}},
        frozenset({"RISK_OFFICER", "ADMIN"}), "VERIFIED_FACT", "/api/v1/risk/dashboard", summarise=_risk_dashboard_summary),
    ToolSpec(
        "get_flagged_transactions", "Flagged / reviewed fraud checks",
        {"type": "object", "properties": {
            "reviewStatus": {"type": "string"},
            "page": {"type": "integer", "default": 0},
            "size": {"type": "integer", "default": 10},
        }},
        frozenset({"RISK_OFFICER", "ADMIN"}), "VERIFIED_FACT", "/api/v1/risk/flagged-transactions",
        summarise=_page_summary("Flagged", ["reference", "agentCode", "amount", "riskLevel", "reviewStatus", "decision"])),
    ToolSpec(
        "get_investigations", "Open or recent fraud investigations",
        {"type": "object", "properties": {
            "status": {"type": "string"},
            "page": {"type": "integer", "default": 0},
            "size": {"type": "integer", "default": 10},
        }},
        frozenset({"RISK_OFFICER", "ADMIN"}), "VERIFIED_FACT", "/api/v1/risk/investigations",
        summarise=_page_summary("Investigations", ["reference", "agentCode", "severity", "status"])),
    ToolSpec(
        "get_risk_agents", "Agents ranked by risk signals",
        {"type": "object", "properties": {}},
        frozenset({"RISK_OFFICER", "ADMIN"}), "VERIFIED_FACT", "/api/v1/risk/agents",
        summarise=lambda data: _page_summary("Risk agents", ["agentCode", "riskLevel", "creditScore", "flaggedTransactions30d", "openInvestigations"])(
            {"content": data, "totalElements": len(data)} if isinstance(data, list) else data)),
    ToolSpec(
        "get_agent_credit_score", "Latest credit score for an agent (PREDICTION)",
        {"type": "object", "properties": {"agent_id": {"type": "string"}}, "required": ["agent_id"]},
        frozenset({"RISK_OFFICER", "FINANCE_OFFICER", "ADMIN"}), "PREDICTION",
        "/api/v1/credit-scores/agents/{agent_id}", path_args=frozenset({"agent_id"}), summarise=_credit_summary),
    ToolSpec(
        "get_agent_directory", "Search the agent directory",
        {"type": "object", "properties": {
            "q": {"type": "string"},
            "page": {"type": "integer", "default": 0},
            "size": {"type": "integer", "default": 10},
        }},
        frozenset({"RISK_OFFICER", "FINANCE_OFFICER", "ADMIN"}), "VERIFIED_FACT", "/api/v1/agents",
        summarise=_page_summary("Agents", ["agentCode", "businessName", "areaCode", "status", "riskLevel", "creditScore"])),
    ToolSpec(
        "get_agent_balances", "Balances for any agent the caller may view",
        {"type": "object", "properties": {"agent_id": {"type": "string"}}, "required": ["agent_id"]},
        frozenset({"RISK_OFFICER", "FINANCE_OFFICER", "ADMIN"}), "VERIFIED_FACT",
        "/api/v1/agents/{agent_id}/balances", path_args=frozenset({"agent_id"}), summarise=_balances_summary),
    ToolSpec(
        "get_agent_forecast", "Float prediction for an agent (PREDICTION)",
        {"type": "object", "properties": {"agent_id": {"type": "string"}}, "required": ["agent_id"]},
        frozenset({"RISK_OFFICER", "FINANCE_OFFICER", "ADMIN", "AREA_MANAGER"}), "PREDICTION",
        "/api/v1/predictions/agents/{agent_id}", path_args=frozenset({"agent_id"}), summarise=_prediction_summary),
    ToolSpec(
        "get_finance_advances", "Float advances awaiting or under finance review",
        {"type": "object", "properties": {
            "status": {"type": "string"},
            "agentId": {"type": "string"},
            "page": {"type": "integer", "default": 0},
            "size": {"type": "integer", "default": 10},
        }},
        frozenset({"FINANCE_OFFICER", "ADMIN"}), "VERIFIED_FACT", "/api/v1/finance/advances",
        summarise=_page_summary("Advances", ["reference", "agentCode", "status", "requestedAmount", "totalOutstanding"])),
    ToolSpec(
        "get_finance_commissions", "Commission ledger entries",
        {"type": "object", "properties": {
            "agentId": {"type": "string"},
            "status": {"type": "string"},
            "page": {"type": "integer", "default": 0},
            "size": {"type": "integer", "default": 10},
        }},
        frozenset({"FINANCE_OFFICER", "ADMIN"}), "VERIFIED_FACT", "/api/v1/finance/commissions",
        summarise=_page_summary("Commissions", ["agentCode", "transactionType", "baseAmount", "status"])),
    ToolSpec(
        "get_trial_balance", "Ledger trial balance",
        {"type": "object", "properties": {}},
        frozenset({"FINANCE_OFFICER", "ADMIN"}), "VERIFIED_FACT",
        "/api/v1/finance/reports/trial-balance", summarise=_trial_balance_summary),
    ToolSpec(
        "get_income_statement", "Fee, interest and commission P&L",
        {"type": "object", "properties": {}},
        frozenset({"FINANCE_OFFICER", "ADMIN"}), "VERIFIED_FACT",
        "/api/v1/finance/reports/income-statement", summarise=_income_summary),
    ToolSpec(
        "get_float_position", "System-wide float position",
        {"type": "object", "properties": {}},
        frozenset({"FINANCE_OFFICER", "ADMIN"}), "VERIFIED_FACT",
        "/api/v1/finance/reports/float-position", summarise=_float_position_summary),
    ToolSpec(
        "get_advance_book", "Advance book summary by status",
        {"type": "object", "properties": {}},
        frozenset({"FINANCE_OFFICER", "ADMIN"}), "VERIFIED_FACT",
        "/api/v1/finance/reports/advance-book",
        summarise=lambda data: (
            f"Advance book: principal outstanding {_money(data.get('totalPrincipalOutstanding'))}, "
            f"interest outstanding {_money(data.get('totalInterestOutstanding'))}."
            if isinstance(data, dict) else str(data)[:400])),
]


def tools_for_role(role: str) -> list[ToolSpec]:
    return [t for t in TOOLS if role in t.roles]


def openai_tools(role: str) -> list[dict[str, Any]]:
    return [{
        "type": "function",
        "function": {
            "name": t.name,
            "description": f"[{t.classification}] {t.description}",
            "parameters": t.parameters,
        },
    } for t in tools_for_role(role)]


def invoke(tool: ToolSpec, client: BackendClient, args: dict[str, Any], *, agent_id: str | None) -> tuple[bool, str, Any | None]:
    path = tool.path_template
    path_values = dict(args)
    if "agent_id" in tool.path_args and "agent_id" not in path_values and agent_id:
        path_values["agent_id"] = agent_id
    for key in tool.path_args:
        value = path_values.get(key)
        if value is None:
            return False, f"Missing required path argument '{key}'", None
        path = path.replace("{" + key + "}", str(value))
    query = {k: v for k, v in args.items() if k not in tool.path_args}
    query.update(tool.static_params)
    try:
        data = client.get(path, query)
    except BackendError as exc:
        return False, f"ERROR ({exc.status_code}): {exc.message}", None
    summary = tool.summarise(data) if tool.summarise else str(data)[:500]
    return True, summary, data
