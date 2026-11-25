package com.agentbanking.platform.advance;

public enum RepaymentSource {
    /** Agent repays from its own e-float wallet. */
    AGENT_EFLOAT,
    /** Deducted from a commission disbursement. */
    COMMISSION_DEDUCTION,
    /** Finance officer records a repayment received outside the agent wallet (simulated bank settlement). */
    FINANCE_RECORDED
}
