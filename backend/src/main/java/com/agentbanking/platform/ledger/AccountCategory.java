package com.agentbanking.platform.ledger;

public enum AccountCategory {
    ASSET(EntrySide.DEBIT),
    LIABILITY(EntrySide.CREDIT),
    EQUITY(EntrySide.CREDIT),
    REVENUE(EntrySide.CREDIT),
    EXPENSE(EntrySide.DEBIT);

    private final EntrySide normalSide;

    AccountCategory(EntrySide normalSide) {
        this.normalSide = normalSide;
    }

    public EntrySide normalSide() {
        return normalSide;
    }
}
