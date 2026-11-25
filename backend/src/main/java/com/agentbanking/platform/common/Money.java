package com.agentbanking.platform.common;

import com.agentbanking.platform.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Monetary arithmetic helpers. All amounts are BigDecimal with 2 decimal places, banker's rounding. */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {
    }

    public static BigDecimal of(BigDecimal value) {
        return value == null ? ZERO : value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(String value) {
        return of(new BigDecimal(value));
    }

    public static BigDecimal of(long value) {
        return BigDecimal.valueOf(value).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, ROUNDING);
    }

    /** amount * percent / 100, rounded to cents. */
    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return of(amount.multiply(percent).divide(HUNDRED, 10, ROUNDING));
    }

    /** Truncates toward zero to whole cents; used for caps so we never deduct more than allowed. */
    public static BigDecimal floor(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.DOWN);
    }

    public static BigDecimal roundUpTo(BigDecimal value, BigDecimal step) {
        if (value.signum() <= 0) {
            return ZERO;
        }
        return of(value.divide(step, 0, RoundingMode.CEILING).multiply(step));
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    public static BigDecimal requireValidAmount(BigDecimal amount, String field) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", field + " must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", field + " cannot have more than 2 decimal places");
        }
        return of(amount);
    }
}
