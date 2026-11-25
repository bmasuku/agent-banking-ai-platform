package com.agentbanking.platform.risk;

public enum InvestigationStatus {
    OPEN, IN_PROGRESS, ESCALATED, CLOSED;

    public boolean isOpen() {
        return this != CLOSED;
    }
}
