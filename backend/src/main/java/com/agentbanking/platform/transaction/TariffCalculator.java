package com.agentbanking.platform.transaction;

import com.agentbanking.platform.common.Money;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class TariffCalculator {

    private TariffCalculator() {
    }

    public static Optional<TariffRule> match(List<TariffRule> rules, BigDecimal amount) {
        return rules.stream()
                .filter(TariffRule::isActive)
                .filter(r -> amount.compareTo(r.getMinAmount()) >= 0 && amount.compareTo(r.getMaxAmount()) <= 0)
                .findFirst();
    }

    public static TariffQuote calculate(TariffRule rule, BigDecimal amount) {
        BigDecimal fee = Money.percentOf(amount, rule.getFeePercent()).add(Money.of(rule.getFeeFlat()));
        BigDecimal commission = Money.percentOf(amount, rule.getCommissionPercent()).add(Money.of(rule.getCommissionFlat()));
        return new TariffQuote(rule.getId(), rule.getTransactionType(), Money.of(amount), Money.of(fee), Money.of(commission),
                rule.getFeePercent(), rule.getFeeFlat(), rule.getCommissionPercent(), rule.getCommissionFlat());
    }

    public record TariffQuote(UUID ruleId, TransactionType type, BigDecimal amount, BigDecimal fee, BigDecimal commission,
                              BigDecimal feePercent, BigDecimal feeFlat, BigDecimal commissionPercent,
                              BigDecimal commissionFlat) {
    }
}
