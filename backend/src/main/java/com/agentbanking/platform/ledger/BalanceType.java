package com.agentbanking.platform.ledger;

/**
 * Chart of accounts. Each balance type fixes the accounting category (and therefore the normal side)
 * and the owner of the account. Agent e-float and customer wallets are platform liabilities (e-money
 * owed to the holder); agent cash and advance receivables are tracked as assets.
 */
public enum BalanceType {
    EFLOAT(AccountCategory.LIABILITY, OwnerType.AGENT, false, "E-float wallet"),
    CASH(AccountCategory.ASSET, OwnerType.AGENT, false, "Cash in hand"),
    COMMISSION_PAYABLE(AccountCategory.LIABILITY, OwnerType.AGENT, false, "Accrued commission payable"),
    ADVANCE_PRINCIPAL(AccountCategory.ASSET, OwnerType.AGENT, false, "Float advance principal receivable"),
    ADVANCE_INTEREST(AccountCategory.ASSET, OwnerType.AGENT, false, "Float advance interest receivable"),

    CUSTOMER_WALLET(AccountCategory.LIABILITY, OwnerType.CUSTOMER, false, "Customer e-money wallet"),

    AREA_EFLOAT_POOL(AccountCategory.LIABILITY, OwnerType.AREA, false, "Area e-float allocation pool"),
    AREA_CASH_POOL(AccountCategory.ASSET, OwnerType.AREA, false, "Area cash vault"),

    SETTLEMENT_BANK(AccountCategory.ASSET, OwnerType.SYSTEM, false, "Simulated trust/settlement bank account"),
    CENTRAL_CASH_VAULT(AccountCategory.ASSET, OwnerType.SYSTEM, false, "Central cash vault"),
    CASH_CONTROL(AccountCategory.LIABILITY, OwnerType.SYSTEM, true, "Customer physical cash settlement control"),
    OPENING_EQUITY(AccountCategory.EQUITY, OwnerType.SYSTEM, false, "Opening balance equity"),
    FEE_INCOME(AccountCategory.REVENUE, OwnerType.SYSTEM, false, "Transaction fee income"),
    INTEREST_INCOME(AccountCategory.REVENUE, OwnerType.SYSTEM, false, "Float advance interest income"),
    COMMISSION_EXPENSE(AccountCategory.EXPENSE, OwnerType.SYSTEM, false, "Agent commission expense");

    private final AccountCategory category;
    private final OwnerType ownerType;
    private final boolean allowNegative;
    private final String label;

    BalanceType(AccountCategory category, OwnerType ownerType, boolean allowNegative, String label) {
        this.category = category;
        this.ownerType = ownerType;
        this.allowNegative = allowNegative;
        this.label = label;
    }

    public AccountCategory category() {
        return category;
    }

    public OwnerType ownerType() {
        return ownerType;
    }

    public boolean allowNegative() {
        return allowNegative;
    }

    public String label() {
        return label;
    }

    public String errorCode() {
        return switch (this) {
            case EFLOAT -> "INSUFFICIENT_EFLOAT";
            case CASH -> "INSUFFICIENT_CASH";
            case CUSTOMER_WALLET -> "INSUFFICIENT_CUSTOMER_BALANCE";
            case AREA_EFLOAT_POOL, AREA_CASH_POOL -> "INSUFFICIENT_AREA_POOL";
            case COMMISSION_PAYABLE -> "INSUFFICIENT_COMMISSION";
            case CENTRAL_CASH_VAULT -> "INSUFFICIENT_CENTRAL_VAULT";
            default -> "INSUFFICIENT_FUNDS";
        };
    }
}
