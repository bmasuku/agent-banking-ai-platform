package com.agentbanking.platform.transaction;

import com.agentbanking.platform.transaction.TariffCalculator.TariffQuote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TariffCalculatorTest {

    private final List<TariffRule> withdrawalRules = TariffService.defaultRules().stream()
            .filter(r -> r.getTransactionType() == TransactionType.CASH_WITHDRAWAL).toList();

    @Test
    void picksTheBandContainingTheAmount() {
        assertThat(TariffCalculator.match(withdrawalRules, new BigDecimal("100.00")).orElseThrow().getMaxAmount())
                .isEqualByComparingTo("100.00");
        assertThat(TariffCalculator.match(withdrawalRules, new BigDecimal("100.01")).orElseThrow().getMinAmount())
                .isEqualByComparingTo("100.01");
        assertThat(TariffCalculator.match(withdrawalRules, new BigDecimal("2500")).orElseThrow().getMinAmount())
                .isEqualByComparingTo("1000.01");
    }

    @Test
    void calculatesPercentagePlusFlatFeeAndCommission() {
        TariffRule small = TariffCalculator.match(withdrawalRules, new BigDecimal("80.00")).orElseThrow();
        TariffQuote q = TariffCalculator.calculate(small, new BigDecimal("80.00"));
        assertThat(q.fee()).isEqualByComparingTo("1.45");
        assertThat(q.commission()).isEqualByComparingTo("0.48");
    }

    @Test
    void depositsAreFreeForCustomersButEarnCommission() {
        TariffRule deposit = TariffService.defaultRules().stream()
                .filter(r -> r.getTransactionType() == TransactionType.CASH_DEPOSIT).findFirst().orElseThrow();
        TariffQuote q = TariffCalculator.calculate(deposit, new BigDecimal("1000.00"));
        assertThat(q.fee()).isEqualByComparingTo("0.00");
        assertThat(q.commission()).isEqualByComparingTo("5.00");
    }

    @Test
    void roundsHalfEvenToCents() {
        TariffRule rule = TariffRule.of(TransactionType.AGENT_TRANSFER_TO_CUSTOMER, "0.01", "1000", "0", "0", "0.25", "0");
        assertThat(TariffCalculator.calculate(rule, new BigDecimal("2.00")).commission()).isEqualByComparingTo("0.00");
        assertThat(TariffCalculator.calculate(rule, new BigDecimal("6.00")).commission()).isEqualByComparingTo("0.02");
        assertThat(TariffCalculator.calculate(rule, new BigDecimal("10.10")).commission()).isEqualByComparingTo("0.03");
    }
}
