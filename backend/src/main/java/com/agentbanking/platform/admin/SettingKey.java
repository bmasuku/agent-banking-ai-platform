package com.agentbanking.platform.admin;

import java.util.Arrays;
import java.util.Optional;

/** Runtime-configurable platform policy. Defaults are inserted on startup and restorable via demo reset. */
public enum SettingKey {

    TRANSACTION_MIN_AMOUNT("transaction.min_amount", "1.00", ValueType.DECIMAL, "TRANSACTIONS",
            "Minimum amount for a single customer transaction"),
    TRANSACTION_MAX_AMOUNT("transaction.max_amount", "5000.00", ValueType.DECIMAL, "TRANSACTIONS",
            "Maximum amount for a single customer transaction"),

    COMMISSION_RECOVERY_ENABLED("commission.recovery.enabled", "true", ValueType.BOOLEAN, "COMMISSIONS",
            "Recover outstanding float advance balances from commission disbursements"),
    COMMISSION_RECOVERY_MAX_PERCENT("commission.recovery.max_percent", "50", ValueType.DECIMAL, "COMMISSIONS",
            "Maximum percentage of gross commission that may be applied to advance recovery (0-100)"),
    COMMISSION_RECOVERY_ORDER("commission.recovery.order", "INTEREST_FIRST", ValueType.ENUM, "COMMISSIONS",
            "Allocation order for commission recovery: INTEREST_FIRST, PRINCIPAL_FIRST or PRO_RATA"),
    COMMISSION_MIN_PAYOUT("commission.min_payout", "5.00", ValueType.DECIMAL, "COMMISSIONS",
            "Minimum accrued commission required before a disbursement can be made"),

    ADVANCE_REPAYMENT_ORDER("advance.repayment.order", "INTEREST_FIRST", ValueType.ENUM, "ADVANCES",
            "Allocation order for direct advance repayments: INTEREST_FIRST, PRINCIPAL_FIRST or PRO_RATA"),
    ADVANCE_MIN_AMOUNT("advance.min_amount", "50.00", ValueType.DECIMAL, "ADVANCES", "Minimum float advance amount"),
    ADVANCE_MAX_TERM_DAYS("advance.max_term_days", "30", ValueType.INTEGER, "ADVANCES", "Maximum float advance term in days"),
    ADVANCE_AUTO_APPROVE_MAX_AMOUNT("advance.auto_approve.max_amount", "2000.00", ValueType.DECIMAL, "ADVANCES",
            "Largest advance that may be approved automatically"),
    ADVANCE_AUTO_APPROVE_MIN_SCORE("advance.auto_approve.min_score", "720", ValueType.INTEGER, "ADVANCES",
            "Minimum credit score (0-1000 scale) for automatic approval"),
    ADVANCE_PENALTY_DAILY_RATE("advance.penalty_daily_rate", "0.0010", ValueType.DECIMAL, "ADVANCES",
            "Daily penalty interest rate applied to overdue principal"),

    CREDIT_LIMIT_CAP_LOW("credit.limit_cap.low", "10000.00", ValueType.DECIMAL, "CREDIT", "Maximum advance limit for LOW risk agents"),
    CREDIT_LIMIT_CAP_MEDIUM("credit.limit_cap.medium", "5000.00", ValueType.DECIMAL, "CREDIT", "Maximum advance limit for MEDIUM risk agents"),
    CREDIT_LIMIT_CAP_HIGH("credit.limit_cap.high", "1500.00", ValueType.DECIMAL, "CREDIT", "Maximum advance limit for HIGH risk agents"),
    CREDIT_DAILY_RATE_LOW("credit.daily_rate.low", "0.0008", ValueType.DECIMAL, "CREDIT", "Daily interest rate for LOW risk agents"),
    CREDIT_DAILY_RATE_MEDIUM("credit.daily_rate.medium", "0.0012", ValueType.DECIMAL, "CREDIT", "Daily interest rate for MEDIUM risk agents"),
    CREDIT_DAILY_RATE_HIGH("credit.daily_rate.high", "0.0018", ValueType.DECIMAL, "CREDIT", "Daily interest rate for HIGH risk agents"),
    CREDIT_DAILY_RATE_CRITICAL("credit.daily_rate.critical", "0.0025", ValueType.DECIMAL, "CREDIT", "Daily interest rate for CRITICAL risk agents"),

    FRAUD_REVIEW_THRESHOLD("fraud.review_threshold", "0.60", ValueType.DECIMAL, "RISK",
            "Fraud probability at or above which a transaction is flagged for review"),
    FRAUD_BLOCK_THRESHOLD("fraud.block_threshold", "0.90", ValueType.DECIMAL, "RISK",
            "Fraud probability at or above which a transaction is blocked"),
    FRAUD_REPORTING_THRESHOLDS("fraud.reporting_thresholds", "500,1000,5000", ValueType.STRING, "RISK",
            "Comma-separated amounts used to detect structuring just below reporting thresholds"),

    EXCHANGE_EXPIRY_HOURS("exchange.expiry_hours", "24", ValueType.INTEGER, "FLOAT", "Hours before a pending float exchange expires"),

    AI_SERVICE_ENABLED("ai.enabled", "true", ValueType.BOOLEAN, "AI",
            "Use the AI intelligence service; when false or unreachable, explainable rule-based models are used");

    public enum ValueType { STRING, DECIMAL, INTEGER, BOOLEAN, ENUM }

    private final String key;
    private final String defaultValue;
    private final ValueType type;
    private final String category;
    private final String description;

    SettingKey(String key, String defaultValue, ValueType type, String category, String description) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.type = type;
        this.category = category;
        this.description = description;
    }

    public String key() {
        return key;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public ValueType type() {
        return type;
    }

    public String category() {
        return category;
    }

    public String description() {
        return description;
    }

    public static Optional<SettingKey> fromKey(String key) {
        return Arrays.stream(values()).filter(k -> k.key.equals(key)).findFirst();
    }
}
