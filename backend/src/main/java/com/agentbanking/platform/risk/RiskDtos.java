package com.agentbanking.platform.risk;

import com.agentbanking.platform.agent.AgentStatus;
import com.agentbanking.platform.ai.FeatureContribution;
import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.common.RiskLevel;
import com.agentbanking.platform.fraud.FraudCheck;
import com.agentbanking.platform.fraud.FraudDecision;
import com.agentbanking.platform.fraud.ReviewStatus;
import com.agentbanking.platform.transaction.TransactionStatus;
import com.agentbanking.platform.transaction.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RiskDtos {

    private RiskDtos() {
    }

    public record FlaggedTransactionResponse(UUID fraudCheckId, UUID transactionId, String reference, TransactionType transactionType,
                                             TransactionStatus transactionStatus, BigDecimal amount, String currency,
                                             UUID agentId, String agentCode, String customerName, String customerPhone,
                                             BigDecimal score, RiskLevel riskLevel, FraudDecision decision,
                                             ReviewStatus reviewStatus, List<String> reasonCodes, String explanation,
                                             String modelSource, Instant createdAt) {
        public static FlaggedTransactionResponse from(FraudCheck c) {
            var t = c.getTransaction();
            return new FlaggedTransactionResponse(c.getId(), t.getId(), t.getReference(), t.getType(), t.getStatus(),
                    Money.of(t.getAmount()), t.getCurrency(), c.getAgent().getId(), c.getAgent().getAgentCode(),
                    t.getCustomer() == null ? null : t.getCustomer().getFullName(),
                    t.getCustomer() == null ? null : t.getCustomer().getPhoneNumber(),
                    c.getScore(), c.getRiskLevel(), c.getDecision(), c.getReviewStatus(), c.getReasonCodes(), c.getExplanation(),
                    c.getModelSource(), c.getCreatedAt());
        }
    }

    public record FraudCheckDetail(FlaggedTransactionResponse summary, List<FeatureContribution> featureContributions,
                                   Map<String, Object> features, String modelVersion, UUID reviewedBy, String reviewNote,
                                   Instant reviewedAt, UUID investigationId, String investigationCaseNumber) {
    }

    public record ReviewRequest(@NotNull ReviewStatus status, @NotBlank @Size(max = 500) String note) {
    }

    public record CreateInvestigationRequest(@NotNull UUID agentId, UUID fraudCheckId, @NotBlank @Size(max = 200) String title,
                                             @NotBlank @Size(max = 4000) String description, @NotNull RiskLevel severity,
                                             UUID assigneeId) {
    }

    public record AssignRequest(@NotNull UUID assigneeId) {
    }

    public record NoteRequest(@NotBlank @Size(max = 4000) String note) {
    }

    public record StatusRequest(@NotNull InvestigationStatus status, @Size(max = 500) String note) {
    }

    public record OutcomeRequest(@NotNull InvestigationOutcome outcome, List<InvestigationAction> actions,
                                 @NotBlank @Size(max = 4000) String resolutionNotes) {
    }

    public record AgentStatusRequest(@NotNull AgentStatus status, @NotBlank @Size(max = 500) String reason) {
    }

    public record InvestigationResponse(UUID id, String caseNumber, UUID agentId, String agentCode, String agentBusinessName,
                                        UUID fraudCheckId, String title, String description, RiskLevel severity,
                                        InvestigationStatus status, InvestigationOutcome outcome, List<String> actionsTaken,
                                        String resolutionNotes, UUID assignedToId, String assignedToName, UUID openedById,
                                        String openedByName, Instant openedAt, Instant updatedAt, Instant closedAt) {
        public static InvestigationResponse from(Investigation i) {
            return new InvestigationResponse(i.getId(), i.getCaseNumber(), i.getAgent().getId(), i.getAgent().getAgentCode(),
                    i.getAgent().getBusinessName(), i.getFraudCheck() == null ? null : i.getFraudCheck().getId(), i.getTitle(),
                    i.getDescription(), i.getSeverity(), i.getStatus(), i.getOutcome(), i.getActionsTaken(), i.getResolutionNotes(),
                    i.getAssignedTo() == null ? null : i.getAssignedTo().getId(),
                    i.getAssignedTo() == null ? null : i.getAssignedTo().getFullName(),
                    i.getOpenedBy() == null ? null : i.getOpenedBy().getId(),
                    i.getOpenedBy() == null ? null : i.getOpenedBy().getFullName(),
                    i.getOpenedAt(), i.getUpdatedAt(), i.getClosedAt());
        }
    }

    public record NoteResponse(UUID id, UUID authorId, String authorName, String note, Instant createdAt) {
        public static NoteResponse from(InvestigationNote n) {
            return new NoteResponse(n.getId(), n.getAuthor() == null ? null : n.getAuthor().getId(),
                    n.getAuthor() == null ? "system" : n.getAuthor().getFullName(), n.getNote(), n.getCreatedAt());
        }
    }

    public record InvestigationDetail(InvestigationResponse investigation, FlaggedTransactionResponse fraudCheck,
                                      List<NoteResponse> notes) {
    }

    public record AgentRiskResponse(UUID agentId, String agentCode, String businessName, String areaCode, AgentStatus status,
                                    RiskLevel riskLevel, int creditScore, long flaggedTransactions30d, long pendingReviews,
                                    long openInvestigations) {
    }

    public record RiskDashboard(long pendingReviews, long openInvestigations, Map<String, Long> investigationsBySeverity,
                                long flaggedLast24h, long blockedLast24h, List<AgentRiskResponse> highestRiskAgents) {
    }
}
