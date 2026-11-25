package com.agentbanking.platform.advance;

public enum AdvanceStatus {
    PENDING, ACTIVE, OVERDUE, REPAID, REJECTED;

    public boolean isOutstanding() {
        return this == ACTIVE || this == OVERDUE;
    }
}
