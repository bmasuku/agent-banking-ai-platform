package com.agentbanking.platform.transaction;

import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.common.RiskLevel;
import com.agentbanking.platform.fraud.FraudCheck;
import com.agentbanking.platform.fraud.FraudDecision;
import com.agentbanking.platform.ledger.BalanceSnapshot;
import com.agentbanking.platform.ledger.LedgerQueryService.LedgerEntryView;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TransactionDtos {

    private TransactionDtos() {
    }

    /**
     * Customer-facing agent transaction. {@code customerName} is required only when the phone number is
     * not yet registered (deposits and transfers register new customers).
     */
    public record CustomerTransactionRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank @Size(max = 32) String customerPhone,
            @Size(max = 160) String customerName,
            @Size(max = 48) String customerNationalId,
            @Size(max = 140) String narration,
            @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude) {
    }

    public record QuoteRequest(@NotNull TransactionType type, @NotNull @DecimalMin("0.01") BigDecimal amount) {
    }

    /**
     * Server-side preview of fees, commission and the exact effect on the agent's balances.
     */
    public record QuoteResponse(TransactionType type, BigDecimal amount, BigDecimal fee, BigDecimal commission,
                                BigDecimal customerPays, BigDecimal customerReceives, BigDecimal efloatChange,
                                BigDecimal cashChange, BigDecimal currentEfloat, BigDecimal currentCash,
                                BigDecimal projectedEfloat, BigDecimal projectedCash, boolean sufficientLiquidity,
                                String explanation) {
    }

    public record FraudSummary(UUID fraudCheckId, BigDecimal score, RiskLevel riskLevel, FraudDecision decision,
                               boolean flagged, List<String> reasonCodes, String explanation, String modelSource) {
        public static FraudSummary from(FraudCheck c) {
            return c == null ? null : new FraudSummary(c.getId(), c.getScore(), c.getRiskLevel(), c.getDecision(), c.isFlagged(),
                    c.getReasonCodes(), c.getExplanation(), c.getModelSource());
        }
    }

    public record CustomerRef(UUID id, String customerNumber, String fullName, String phoneNumber) {
    }

    public record TransactionResponse(UUID id, String reference, TransactionType type, TransactionStatus status,
                                      UUID agentId, String agentCode, UUID counterpartyAgentId, String counterpartyAgentCode,
                                      CustomerRef customer, BigDecimal amount, BigDecimal fee, BigDecimal commission,
                                      String currency, String description, String failureReason, Instant createdAt,
                                      BalanceSnapshot balancesAfter, FraudSummary fraud, List<LedgerEntryView> ledgerEntries,
                                      boolean replayed) {
    }

    public record TransactionSummary(UUID id, String reference, TransactionType type, TransactionStatus status, UUID agentId,
                                     String agentCode, String counterpartyAgentCode, String customerName, String customerPhone,
                                     BigDecimal amount, BigDecimal fee, BigDecimal commission, String currency,
                                     String description, Instant createdAt) {
        public static TransactionSummary from(FinancialTransaction t) {
            return new TransactionSummary(t.getId(), t.getReference(), t.getType(), t.getStatus(),
                    t.getAgent() == null ? null : t.getAgent().getId(),
                    t.getAgent() == null ? null : t.getAgent().getAgentCode(),
                    t.getCounterpartyAgent() == null ? null : t.getCounterpartyAgent().getAgentCode(),
                    t.getCustomer() == null ? null : t.getCustomer().getFullName(),
                    t.getCustomer() == null ? null : t.getCustomer().getPhoneNumber(),
                    Money.of(t.getAmount()), Money.of(t.getFeeAmount()), Money.of(t.getCommissionAmount()), t.getCurrency(),
                    t.getDescription(), t.getCreatedAt());
        }
    }

    /** Result of an engine call: {@code created} is false for idempotent replays. */
    public record TransactionResult(TransactionResponse response, boolean created) {
        public boolean blocked() {
            return response.status() == TransactionStatus.BLOCKED;
        }
    }
}
