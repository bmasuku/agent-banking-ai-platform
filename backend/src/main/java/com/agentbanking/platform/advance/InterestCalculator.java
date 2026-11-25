package com.agentbanking.platform.advance;

import com.agentbanking.platform.common.Money;

import java.math.BigDecimal;

/** Simple (flat) interest: principal x daily rate x days, rounded to cents. */
public final class InterestCalculator {

    private InterestCalculator() {
    }

    public static BigDecimal flatInterest(BigDecimal principal, BigDecimal dailyRate, long days) {
        if (days <= 0 || principal.signum() <= 0 || dailyRate.signum() <= 0) {
            return Money.ZERO;
        }
        return Money.of(principal.multiply(dailyRate).multiply(BigDecimal.valueOf(days)));
    }
}
