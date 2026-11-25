package com.agentbanking.platform.user;

public enum Role {
    AGENT, AREA_MANAGER, RISK_OFFICER, FINANCE_OFFICER, ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }

    public boolean isMobileRole() {
        return this == AGENT || this == AREA_MANAGER;
    }
}
