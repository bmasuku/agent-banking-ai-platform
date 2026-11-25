package com.agentbanking.platform.agent;

public enum AgentStatus {
    ACTIVE, UNDER_REVIEW, FROZEN, SUSPENDED;

    /** Under-review agents keep serving customers under heightened monitoring; frozen/suspended agents cannot transact. */
    public boolean canTransact() {
        return this == ACTIVE || this == UNDER_REVIEW;
    }
}
