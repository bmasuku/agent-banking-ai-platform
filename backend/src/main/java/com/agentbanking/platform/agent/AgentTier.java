package com.agentbanking.platform.agent;

import com.agentbanking.platform.common.RiskLevel;

public enum AgentTier {
    PRIME, STANDARD, WATCHLIST, RESTRICTED;

    public static AgentTier fromRisk(RiskLevel risk) {
        return switch (risk) {
            case LOW -> PRIME;
            case MEDIUM -> STANDARD;
            case HIGH -> WATCHLIST;
            case CRITICAL -> RESTRICTED;
        };
    }
}
