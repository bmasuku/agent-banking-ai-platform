package com.agentbanking.platform.transaction;

public enum TransactionStatus {
    /** Posted to the ledger. */
    COMPLETED,
    /** Stopped by fraud controls before posting; no ledger entries exist. */
    BLOCKED,
    REVERSED
}
