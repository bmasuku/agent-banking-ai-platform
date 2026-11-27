package com.agentbanking.platform.transaction;

public enum TransactionType {
    /** Customer hands the agent cash; the agent sends e-money to the customer's wallet. E-float down, cash up. */
    CASH_DEPOSIT,
    /** Customer sends e-money to the agent and receives cash. E-float up, cash down. */
    CASH_WITHDRAWAL,
    /** Agent sends e-money from its own float to a customer wallet. E-float down. */
    AGENT_TRANSFER_TO_CUSTOMER,
    FLOAT_EXCHANGE,
    FLOAT_REPLENISHMENT,
    /** Agent float returned to the area pool (inverse of replenishment). */
    FLOAT_RECALL,
    AREA_POOL_FUNDING,
    ADVANCE_DISBURSEMENT,
    ADVANCE_REPAYMENT,
    ADVANCE_PENALTY_INTEREST,
    COMMISSION_DISBURSEMENT,
    OPENING_BALANCE;

    public boolean isCustomerTransaction() {
        return this == CASH_DEPOSIT || this == CASH_WITHDRAWAL || this == AGENT_TRANSFER_TO_CUSTOMER;
    }

    /**
     * Cash-in / cash-out require the customer to present identification to the agent in person,
     * so agent–customer distance is always treated as zero.
     */
    public boolean requiresInPersonCustomerPresence() {
        return this == CASH_DEPOSIT || this == CASH_WITHDRAWAL;
    }
}
