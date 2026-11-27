package com.agentbanking.platform.simulation;

public enum SimulationScenario {
    MORNING_RUSH_WITHDRAWALS("Cash withdrawals spread across the area's agents, draining cash-in-hand"),
    MARKET_DAY_DEPOSITS("Customer cash deposits spread across agents, draining e-float"),
    MIXED_TRADING_HOUR("A normal hour of mixed deposits, withdrawals and transfers"),
    FRAUD_STRUCTURING_ATTACK("Rapid deposits just below reporting thresholds at one agent, exercising fraud controls"),
    PEER_REBALANCING_CYCLE("Agents with surplus e-float exchange it for cash with agents short of e-float"),

    NORMAL_AGENT_TRANSACTIONS("Mixed deposits, withdrawals and transfers across agents — typical trading hour"),
    CUSTOMER_DEPOSITS("Customer cash deposits across agents, exercising deposit limits and e-float debits"),
    CUSTOMER_WITHDRAWALS("Cash withdrawals across agents, draining physical cash holdings"),
    INSUFFICIENT_FLOAT("Deliberately oversized transfers that exceed agent e-float to trigger liquidity failures"),
    FLOAT_EXCHANGE_REQUESTS("Peer float exchange proposals between agents; only some are accepted, others stay pending"),
    SUSPICIOUS_TRANSACTIONS("Rapid, large outbound transfers at focused agents to exercise fraud and velocity rules"),
    LOCATION_MISMATCH_ALERTS("Remote e-float sends geo-tagged far from the agent's registered location (e.g. Bulawayo); cash-in/out stay face-to-face"),
    LOAN_DEFAULT_PRESSURE("Float advance requests and penalty accrual to stress overdue repayment scenarios"),
    FLOAT_ADVANCE_REQUESTS("Agents submit float advance applications through the standard credit pipeline"),
    ACCUMULATE_COMMISSIONS("Many small customer transactions to build accrued commission balances"),
    COMMISSION_DISBURSEMENT("Run a full commission batch payout for all eligible agents"),
    ACCELERATED_ACTIVITY("High-volume mixed trading across all target agents"),
    FLOAT_PREDICTIONS("Generate 24-hour float demand predictions for each target agent"),
    AREA_MANAGER_ALERTS("Heavy deposit activity to deplete e-float toward low-liquidity thresholds and alerts");

    private final String description;

    SimulationScenario(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}