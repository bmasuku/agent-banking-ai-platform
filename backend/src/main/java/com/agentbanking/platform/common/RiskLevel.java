package com.agentbanking.platform.common;

public enum RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL;

    public boolean atLeast(RiskLevel other) {
        return ordinal() >= other.ordinal();
    }
}
