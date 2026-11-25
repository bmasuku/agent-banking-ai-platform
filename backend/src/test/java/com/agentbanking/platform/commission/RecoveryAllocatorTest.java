package com.agentbanking.platform.commission;

import com.agentbanking.platform.commission.RecoveryAllocator.Allocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveryAllocatorTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void interestFirstRespectsPercentageCap() {
        Allocation a = RecoveryAllocator.allocate(d("100.00"), d("50"), d("12.00"), d("1500.00"), RecoveryOrder.INTEREST_FIRST);
        assertThat(a.cap()).isEqualByComparingTo("50.00");
        assertThat(a.interestRecovered()).isEqualByComparingTo("12.00");
        assertThat(a.principalRecovered()).isEqualByComparingTo("38.00");
        assertThat(a.net()).isEqualByComparingTo("50.00");
        assertThat(a.outstandingAfter()).isEqualByComparingTo("1462.00");
    }

    @Test
    void principalFirstAppliesToPrincipalBeforeInterest() {
        Allocation a = RecoveryAllocator.allocate(d("100.00"), d("100"), d("30.00"), d("80.00"), RecoveryOrder.PRINCIPAL_FIRST);
        assertThat(a.principalRecovered()).isEqualByComparingTo("80.00");
        assertThat(a.interestRecovered()).isEqualByComparingTo("20.00");
        assertThat(a.net()).isEqualByComparingTo("0.00");
    }

    @Test
    void proRataSplitsInProportionToBalances() {
        Allocation a = RecoveryAllocator.allocate(d("40.00"), d("100"), d("25.00"), d("75.00"), RecoveryOrder.PRO_RATA);
        assertThat(a.interestRecovered()).isEqualByComparingTo("10.00");
        assertThat(a.principalRecovered()).isEqualByComparingTo("30.00");
    }

    @Test
    void neverRecoversMoreThanOutstanding() {
        Allocation a = RecoveryAllocator.allocate(d("500.00"), d("100"), d("5.00"), d("20.00"), RecoveryOrder.INTEREST_FIRST);
        assertThat(a.totalRecovered()).isEqualByComparingTo("25.00");
        assertThat(a.net()).isEqualByComparingTo("475.00");
        assertThat(a.outstandingAfter()).isEqualByComparingTo("0.00");
    }

    @Test
    void zeroPercentDisablesRecovery() {
        Allocation a = RecoveryAllocator.allocate(d("80.00"), BigDecimal.ZERO, d("5.00"), d("20.00"), RecoveryOrder.INTEREST_FIRST);
        assertThat(a.totalRecovered()).isEqualByComparingTo("0.00");
        assertThat(a.net()).isEqualByComparingTo("80.00");
    }

    @ParameterizedTest
    @EnumSource(RecoveryOrder.class)
    void invariantsHoldForRandomInputs(RecoveryOrder order) {
        Random random = new Random(order.ordinal() + 11L);
        for (int i = 0; i < 2000; i++) {
            BigDecimal available = BigDecimal.valueOf(random.nextInt(100_000), 2);
            BigDecimal pct = BigDecimal.valueOf(random.nextInt(101));
            BigDecimal interest = BigDecimal.valueOf(random.nextInt(50_000), 2);
            BigDecimal principal = BigDecimal.valueOf(random.nextInt(500_000), 2);
            Allocation a = RecoveryAllocator.allocate(available, pct, interest, principal, order);

            assertThat(a.interestRecovered()).isBetween(BigDecimal.ZERO, interest);
            assertThat(a.principalRecovered()).isBetween(BigDecimal.ZERO, principal);
            assertThat(a.totalRecovered()).isLessThanOrEqualTo(a.cap());
            assertThat(a.totalRecovered()).isLessThanOrEqualTo(available);
            assertThat(a.net()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(a.net().add(a.totalRecovered())).isEqualByComparingTo(available);
        }
    }
}
