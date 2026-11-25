package com.agentbanking.platform.risk;

/** Remedial actions that can be recorded when closing an investigation. Status actions are applied to the agent. */
public enum InvestigationAction {
    WARNING_ISSUED,
    AGENT_REINSTATED,
    AGENT_FROZEN,
    AGENT_SUSPENDED,
    DAILY_LIMIT_REDUCED,
    CUSTOMER_BLOCKED,
    ESCALATED_TO_COMPLIANCE
}
