package com.agentbanking.platform.credit;

import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.common.RiskLevel;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Platform credit policy: score bands, limit caps and pricing. Applied on top of any model output so
 * the AI service can inform but never override lending limits. Bands match the AI scorecard (0-1000).
 */
public record CreditPolicy(BigDecimal capLow, BigDecimal capMedium, BigDecimal capHigh,
                           BigDecimal rateLow, BigDecimal rateMedium, BigDecimal rateHigh, BigDecimal rateCritical) {

    /** Share of trailing 30-day customer transaction volume that may be extended as float credit. */
    public static final BigDecimal VOLUME_FACTOR = new BigDecimal("0.25");
    private static final BigDecimal LIMIT_STEP = BigDecimal.valueOf(50);

    public static RiskLevel bandFor(int score) {
        if (score >= 750) {
            return RiskLevel.LOW;
        }
        if (score >= 600) {
            return RiskLevel.MEDIUM;
        }
        if (score >= 450) {
            return RiskLevel.HIGH;
        }
        return RiskLevel.CRITICAL;
    }

    /** Score-implied, uncalibrated estimate — not fitted to observed defaults. */
    public static BigDecimal probabilityOfDefault(int score) {
        double pd = 1.0 / (1.0 + Math.exp((score - 600) / 90.0));
        return BigDecimal.valueOf(pd).setScale(4, RoundingMode.HALF_UP);
    }

    public BigDecimal capFor(RiskLevel band) {
        return switch (band) {
            case LOW -> capLow;
            case MEDIUM -> capMedium;
            case HIGH -> capHigh;
            case CRITICAL -> BigDecimal.ZERO;
        };
    }

    public BigDecimal dailyRateFor(RiskLevel band) {
        return switch (band) {
            case LOW -> rateLow;
            case MEDIUM -> rateMedium;
            case HIGH -> rateHigh;
            case CRITICAL -> rateCritical;
        };
    }

    /** Limit = min(band cap, volume-based capacity, model suggestion), rounded down to a multiple of 50. */
    public BigDecimal limitFor(RiskLevel band, BigDecimal volume30d, BigDecimal modelSuggestion) {
        BigDecimal capacity = volume30d.multiply(VOLUME_FACTOR);
        BigDecimal limit = Money.min(capFor(band), capacity);
        if (modelSuggestion != null && modelSuggestion.signum() >= 0) {
            limit = Money.min(limit, modelSuggestion);
        }
        if (limit.signum() <= 0) {
            return Money.ZERO;
        }
        return Money.of(limit.divide(LIMIT_STEP, 0, RoundingMode.DOWN).multiply(LIMIT_STEP));
    }
}
