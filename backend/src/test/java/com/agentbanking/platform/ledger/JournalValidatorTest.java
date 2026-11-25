package com.agentbanking.platform.ledger;

import com.agentbanking.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JournalValidatorTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void acceptsBalancedMultiLegJournal() {
        assertThatCode(() -> JournalValidator.validate(List.of(
                new PostingLeg(a, EntrySide.DEBIT, new BigDecimal("100.00"), null),
                new PostingLeg(b, EntrySide.CREDIT, new BigDecimal("98.50"), null),
                new PostingLeg(c, EntrySide.CREDIT, new BigDecimal("1.50"), null)))).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnbalancedJournal() {
        assertThatThrownBy(() -> JournalValidator.validate(List.of(
                new PostingLeg(a, EntrySide.DEBIT, new BigDecimal("100.00"), null),
                new PostingLeg(b, EntrySide.CREDIT, new BigDecimal("99.99"), null))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("LEDGER_IMBALANCE");
    }

    @Test
    void rejectsSingleLegZeroOrNegativeAmounts() {
        assertThatThrownBy(() -> JournalValidator.validate(List.of(new PostingLeg(a, EntrySide.DEBIT, BigDecimal.ONE, null))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> JournalValidator.validate(List.of(
                new PostingLeg(a, EntrySide.DEBIT, BigDecimal.ZERO, null),
                new PostingLeg(b, EntrySide.CREDIT, BigDecimal.ZERO, null)))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> JournalValidator.validate(List.of(
                new PostingLeg(a, EntrySide.DEBIT, new BigDecimal("-5"), null),
                new PostingLeg(b, EntrySide.CREDIT, new BigDecimal("-5"), null)))).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsExcessivePrecision() {
        assertThatThrownBy(() -> JournalValidator.validate(List.of(
                new PostingLeg(a, EntrySide.DEBIT, new BigDecimal("1.00001"), null),
                new PostingLeg(b, EntrySide.CREDIT, new BigDecimal("1.00001"), null)))).isInstanceOf(BusinessException.class);
    }
}
