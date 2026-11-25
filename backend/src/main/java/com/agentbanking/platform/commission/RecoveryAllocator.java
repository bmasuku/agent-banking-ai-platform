package com.agentbanking.platform.commission;

import com.agentbanking.platform.common.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure allocation arithmetic for commission recovery and advance repayments.
 * Guarantees: interest + principal recovered never exceeds the cap, the outstanding balances or the
 * available funds, and {@code net = available - interest - principal >= 0}.
 */
public final class RecoveryAllocator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private RecoveryAllocator() {
    }

    public static Allocation allocate(BigDecimal available, BigDecimal maxPercent, BigDecimal interestOutstanding,
                                      BigDecimal principalOutstanding, RecoveryOrder order) {
        BigDecimal funds = Money.of(available);
        BigDecimal interest = Money.of(interestOutstanding).max(Money.ZERO);
        BigDecimal principal = Money.of(principalOutstanding).max(Money.ZERO);
        BigDecimal pct = maxPercent.max(BigDecimal.ZERO).min(HUNDRED);

        BigDecimal cap = Money.floor(funds.multiply(pct).divide(HUNDRED, 10, RoundingMode.DOWN));
        BigDecimal recoverable = Money.min(cap, interest.add(principal));

        BigDecimal ir;
        BigDecimal pr;
        switch (order) {
            case PRINCIPAL_FIRST -> {
                pr = Money.min(recoverable, principal);
                ir = Money.min(recoverable.subtract(pr), interest);
            }
            case PRO_RATA -> {
                BigDecimal total = interest.add(principal);
                ir = total.signum() == 0 ? Money.ZERO
                        : Money.floor(recoverable.multiply(interest).divide(total, 10, RoundingMode.DOWN));
                pr = recoverable.subtract(ir);
                if (pr.compareTo(principal) > 0) {
                    ir = ir.add(pr.subtract(principal));
                    pr = principal;
                }
            }
            default -> {
                ir = Money.min(recoverable, interest);
                pr = Money.min(recoverable.subtract(ir), principal);
            }
        }
        BigDecimal net = funds.subtract(ir).subtract(pr);
        return new Allocation(funds, cap, Money.of(ir), Money.of(pr), Money.of(net),
                Money.of(interest.add(principal)), Money.of(interest.add(principal).subtract(ir).subtract(pr)));
    }

    /**
     * @param available          gross funds considered (commission payable or repayment amount)
     * @param cap                maximum deductible amount given the configured percentage
     * @param interestRecovered  amount applied to interest
     * @param principalRecovered amount applied to principal
     * @param net                remainder paid out to the agent's e-float
     * @param outstandingBefore  interest + principal outstanding before
     * @param outstandingAfter   interest + principal outstanding after
     */
    public record Allocation(BigDecimal available, BigDecimal cap, BigDecimal interestRecovered, BigDecimal principalRecovered,
                             BigDecimal net, BigDecimal outstandingBefore, BigDecimal outstandingAfter) {

        public BigDecimal totalRecovered() {
            return interestRecovered.add(principalRecovered);
        }
    }
}
