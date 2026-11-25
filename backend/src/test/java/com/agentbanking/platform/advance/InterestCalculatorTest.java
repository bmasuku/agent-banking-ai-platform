package com.agentbanking.platform.advance;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InterestCalculatorTest {

    @Test
    void flatInterestIsPrincipalTimesRateTimesDays() {
        assertThat(InterestCalculator.flatInterest(new BigDecimal("1500.00"), new BigDecimal("0.0012"), 14))
                .isEqualByComparingTo("25.20");
        assertThat(InterestCalculator.flatInterest(new BigDecimal("500.00"), new BigDecimal("0.0018"), 7))
                .isEqualByComparingTo("6.30");
    }

    @Test
    void zeroForNonPositiveInputs() {
        assertThat(InterestCalculator.flatInterest(new BigDecimal("100"), new BigDecimal("0.001"), 0)).isEqualByComparingTo("0");
        assertThat(InterestCalculator.flatInterest(BigDecimal.ZERO, new BigDecimal("0.001"), 10)).isEqualByComparingTo("0");
        assertThat(InterestCalculator.flatInterest(new BigDecimal("100"), BigDecimal.ZERO, 10)).isEqualByComparingTo("0");
    }
}
