package com.agentbanking.platform.advance;

import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.Money;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdvanceDtos {

    private AdvanceDtos() {
    }

    public record AdvanceRequest(
            @NotNull @DecimalMin("1.00") BigDecimal amount,
            @NotNull @Min(1) @Max(90) Integer termDays,
            @Size(max = 200) String purpose) {
    }

    /** Manual credit decision by a finance officer. Optional fields default to the requested terms and model pricing. */
    public record ApproveRequest(
            @DecimalMin("1.00") BigDecimal approvedAmount,
            @DecimalMin("0.000000") @DecimalMax("0.050000") BigDecimal dailyInterestRate,
            @Min(1) @Max(90) Integer termDays,
            @NotBlank @Size(max = 500) String note) {
    }

    public record RejectRequest(@NotBlank @Size(max = 500) String note) {
    }

    public record RepaymentRequest(@NotNull @DecimalMin("0.01") BigDecimal amount) {
    }

    public record AdvanceResponse(
            UUID id, String reference, UUID agentId, String agentCode, String agentName,
            BigDecimal requestedAmount, BigDecimal approvedAmount, BigDecimal dailyInterestRate, int termDays,
            BigDecimal interestAmount, BigDecimal penaltyInterestAccrued, BigDecimal principalOutstanding,
            BigDecimal interestOutstanding, BigDecimal totalOutstanding, BigDecimal principalRepaid, BigDecimal interestRepaid,
            AdvanceStatus status, AdvanceDecisionType decisionType, Integer creditScoreAtRequest,
            Map<String, Object> decisionRationale, String purpose, String decisionNote, Instant requestedAt,
            Instant decidedAt, Instant disbursedAt, LocalDate dueDate, long daysOverdue, Instant closedAt,
            UUID disbursementTransactionId, boolean replayed) {

        public static AdvanceResponse from(FloatAdvance a, boolean replayed) {
            long overdue = a.getDueDate() != null && a.getStatus().isOutstanding() && AppClock.today().isAfter(a.getDueDate())
                    ? ChronoUnit.DAYS.between(a.getDueDate(), AppClock.today()) : 0;
            return new AdvanceResponse(a.getId(), a.getReference(), a.getAgent().getId(), a.getAgent().getAgentCode(),
                    a.getAgent().getUser().getFullName(), Money.of(a.getRequestedAmount()),
                    a.getApprovedAmount() == null ? null : Money.of(a.getApprovedAmount()), a.getDailyInterestRate(),
                    a.getTermDays(), Money.of(a.getInterestAmount()), Money.of(a.getPenaltyInterestAccrued()),
                    Money.of(a.getPrincipalOutstanding()), Money.of(a.getInterestOutstanding()), a.totalOutstanding(),
                    Money.of(a.getPrincipalRepaid()), Money.of(a.getInterestRepaid()), a.getStatus(), a.getDecisionType(),
                    a.getCreditScoreAtRequest(), a.getDecisionRationale(), a.getPurpose(), a.getDecisionNote(),
                    a.getRequestedAt(), a.getDecidedAt(), a.getDisbursedAt(), a.getDueDate(), overdue, a.getClosedAt(),
                    a.getDisbursementTransaction() == null ? null : a.getDisbursementTransaction().getId(), replayed);
        }
    }

    public record RepaymentView(UUID id, RepaymentSource source, BigDecimal principalAmount, BigDecimal interestAmount,
                                BigDecimal totalAmount, UUID transactionId, String transactionReference, Instant createdAt) {
        public static RepaymentView from(AdvanceRepayment r) {
            return new RepaymentView(r.getId(), r.getSource(), Money.of(r.getPrincipalAmount()), Money.of(r.getInterestAmount()),
                    Money.of(r.getTotalAmount()), r.getTransaction().getId(), r.getTransaction().getReference(), r.getCreatedAt());
        }
    }

    public record AdvanceDetail(AdvanceResponse advance, List<RepaymentView> repayments) {
    }

    public record RepaymentResult(UUID transactionId, String reference, BigDecimal amount, BigDecimal interestApplied,
                                  BigDecimal principalApplied, AdvanceResponse advance, boolean replayed) {
    }
}
