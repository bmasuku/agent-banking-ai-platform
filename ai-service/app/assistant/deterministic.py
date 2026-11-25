"""Keyword intent router used when no LLM is configured. Labels itself honestly."""
from __future__ import annotations

import re
from dataclasses import dataclass

from app.assistant.backend_client import BackendClient
from app.assistant.schemas import ToolCallRecord
from app.assistant.tools import ToolSpec, invoke, tools_for_role


@dataclass
class IntentPlan:
    tool_names: list[str]
    preface: str


_INTENTS: list[tuple[re.Pattern[str], dict[str, list[str]], str]] = [
    (re.compile(r"\b(balance|efloat|e-?float|cash|liquidity|how much)\b", re.I),
     {"AGENT": ["get_my_balances"],
      "AREA_MANAGER": ["get_area_overview", "get_area_agents"],
      "RISK_OFFICER": ["get_risk_agents"],
      "FINANCE_OFFICER": ["get_float_position"],
      "ADMIN": ["get_float_position"]},
     "Here are the current balances from the platform ledger."),
    (re.compile(r"\b(forecast|prediction|stockout|top-?up|replenish|run out|demand)\b", re.I),
     {"AGENT": ["get_my_float_forecast"],
      "AREA_MANAGER": ["get_area_predictions"],
      "RISK_OFFICER": ["get_risk_dashboard"],
      "FINANCE_OFFICER": ["get_float_position"],
      "ADMIN": ["get_float_position"]},
     "Here is the latest float forecast. These figures are PREDICTIONs, not ledger balances."),
    (re.compile(r"\b(commission|earnings|payable)\b", re.I),
     {"AGENT": ["get_my_commissions"],
      "AREA_MANAGER": ["get_area_report"],
      "FINANCE_OFFICER": ["get_finance_commissions"],
      "ADMIN": ["get_finance_commissions"],
      "RISK_OFFICER": ["get_risk_dashboard"]},
     "Commission information from the platform:"),
    (re.compile(r"\b(advance|loan|credit score|credit limit|repayment)\b", re.I),
     {"AGENT": ["get_my_advances"],
      "AREA_MANAGER": ["get_area_agents"],
      "FINANCE_OFFICER": ["get_finance_advances", "get_advance_book"],
      "ADMIN": ["get_finance_advances", "get_advance_book"],
      "RISK_OFFICER": ["get_risk_agents"]},
     "Advance / credit information:"),
    (re.compile(r"\b(fraud|flagged|alert|investigation|suspicious|risk)\b", re.I),
     {"AGENT": ["get_my_transactions"],
      "AREA_MANAGER": ["get_area_alerts"],
      "RISK_OFFICER": ["get_risk_dashboard", "get_flagged_transactions", "get_investigations"],
      "ADMIN": ["get_risk_dashboard", "get_flagged_transactions"],
      "FINANCE_OFFICER": ["get_trial_balance"]},
     "Risk / fraud information available to your role:"),
    (re.compile(r"\b(transaction|history|statement|activity)\b", re.I),
     {"AGENT": ["get_my_transactions"],
      "AREA_MANAGER": ["get_area_report"],
      "RISK_OFFICER": ["get_flagged_transactions"],
      "FINANCE_OFFICER": ["get_income_statement"],
      "ADMIN": ["get_income_statement"]},
     "Recent activity:"),
    (re.compile(r"\b(trial balance|p&l|income|float position|ledger)\b", re.I),
     {"FINANCE_OFFICER": ["get_trial_balance", "get_income_statement", "get_float_position"],
      "ADMIN": ["get_trial_balance", "get_income_statement", "get_float_position"],
      "AREA_MANAGER": ["get_area_report"],
      "AGENT": ["get_my_balances"],
      "RISK_OFFICER": ["get_risk_dashboard"]},
     "Financial report:"),
    (re.compile(r"\b(area|pool|agents in|my agents|overview)\b", re.I),
     {"AREA_MANAGER": ["get_area_overview", "get_area_agents", "get_area_report"],
      "FINANCE_OFFICER": ["get_float_position"],
      "ADMIN": ["get_float_position"],
      "RISK_OFFICER": ["get_risk_agents"],
      "AGENT": ["get_my_balances"]},
     "Area overview:"),
]


def plan(question: str, role: str) -> IntentPlan:
    available = {t.name for t in tools_for_role(role)}
    for pattern, by_role, preface in _INTENTS:
        if pattern.search(question):
            names = [n for n in by_role.get(role, []) if n in available]
            if names:
                return IntentPlan(names, preface)
    defaults = {
        "AGENT": ["get_my_balances", "get_my_commissions"],
        "AREA_MANAGER": ["get_area_overview", "get_area_agents"],
        "RISK_OFFICER": ["get_risk_dashboard"],
        "FINANCE_OFFICER": ["get_float_position", "get_advance_book"],
        "ADMIN": ["get_risk_dashboard", "get_float_position"],
    }
    names = [n for n in defaults.get(role, ["get_my_profile"]) if n in available]
    return IntentPlan(names or ["get_my_profile"],
                      "I matched this to a general status lookup for your role. Ask about balances, forecasts, "
                      "commissions, advances, fraud alerts or reports for a more specific answer.")


def answer(question: str, role: str, client: BackendClient, *, agent_id: str | None) -> tuple[str, list[ToolCallRecord]]:
    intent = plan(question, role)
    by_name = {t.name: t for t in tools_for_role(role)}
    records: list[ToolCallRecord] = []
    sections: list[str] = [intent.preface]
    for name in intent.tool_names:
        tool: ToolSpec | None = by_name.get(name)
        if tool is None:
            continue
        ok, summary, _ = invoke(tool, client, {}, agent_id=agent_id)
        records.append(ToolCallRecord(
            name=name, arguments={}, classification="ERROR" if not ok else tool.classification,
            summary=summary, ok=ok))
        tag = "ERROR" if not ok else tool.classification
        sections.append(f"[{tag} via {name}] {summary}")
    if not records:
        sections.append("No authorised tools were available for this question.")
    sections.append("Engine: DETERMINISTIC_INTENT_ROUTER (no LLM configured).")
    return "\n\n".join(sections), records
