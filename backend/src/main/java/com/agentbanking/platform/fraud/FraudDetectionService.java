package com.agentbanking.platform.fraud;

import com.agentbanking.platform.admin.SettingKey;
import com.agentbanking.platform.admin.SystemSettingService;
import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.ai.AiServiceClient;
import com.agentbanking.platform.ai.FeatureContribution;
import com.agentbanking.platform.common.RiskLevel;
import com.agentbanking.platform.notification.NotificationService;
import com.agentbanking.platform.notification.NotificationType;
import com.agentbanking.platform.transaction.FinancialTransaction;
import com.agentbanking.platform.user.Role;
import com.agentbanking.platform.websocket.RealtimeEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Fraud detection integration. The AI service (or the rule-based fallback) produces a probability and
 * explanation; this service applies the platform's configurable decision policy, which remains the
 * authority on whether a transaction is allowed, flagged or blocked.
 */
@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private final AiServiceClient aiClient;
    private final SystemSettingService settings;
    private final FraudCheckRepository repository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher events;

    public FraudAssessment assess(FraudFeatures features) {
        FraudAssessment raw = aiClient.post("/internal/ai/v1/fraud/evaluate", features, AiFraudResponse.class)
                .filter(r -> r.fraudProbability() != null)
                .map(this::fromAi)
                .orElseGet(() -> RuleBasedFraudScorer.score(features));
        return applyPolicy(raw);
    }

    FraudAssessment applyPolicy(FraudAssessment raw) {
        BigDecimal review = settings.getDecimal(SettingKey.FRAUD_REVIEW_THRESHOLD);
        BigDecimal block = settings.getDecimal(SettingKey.FRAUD_BLOCK_THRESHOLD);
        BigDecimal score = raw.score();
        FraudDecision decision = score.compareTo(block) >= 0 ? FraudDecision.BLOCK
                : score.compareTo(review) >= 0 ? FraudDecision.REVIEW : FraudDecision.ALLOW;
        RiskLevel level = decision == FraudDecision.BLOCK ? RiskLevel.CRITICAL
                : decision == FraudDecision.REVIEW ? RiskLevel.HIGH
                : score.compareTo(new BigDecimal("0.30")) >= 0 ? RiskLevel.MEDIUM : RiskLevel.LOW;
        return raw.withDecision(decision, level);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public FraudCheck record(FinancialTransaction transaction, Agent agent, FraudFeatures features, FraudAssessment assessment) {
        FraudCheck check = new FraudCheck();
        check.setTransaction(transaction);
        check.setAgent(agent);
        check.setScore(assessment.score());
        check.setRiskLevel(assessment.riskLevel());
        check.setDecision(assessment.decision());
        check.setFlagged(assessment.decision() != FraudDecision.ALLOW);
        check.setReviewStatus(check.isFlagged() ? ReviewStatus.PENDING_REVIEW : ReviewStatus.NOT_REQUIRED);
        check.setReasonCodes(assessment.reasonCodes());
        check.setFeatureContributions(assessment.contributions());
        check.setFeatures(features.toMap());
        check.setExplanation(assessment.explanation());
        check.setModelSource(assessment.modelSource());
        check.setModelVersion(assessment.modelVersion());
        check.setRecommendedAction(assessment.recommendedAction());
        check.setAiPredictionId(assessment.aiPredictionId());
        repository.save(check);

        if (check.isFlagged()) {
            String verb = assessment.decision() == FraudDecision.BLOCK ? "blocked" : "flagged for review";
            String title = "Transaction " + verb + " (" + agent.getAgentCode() + ")";
            String message = String.format("%s %s of %s %s scored %.2f fraud probability. %s",
                    transaction.getReference(), transaction.getType(), transaction.getAmount().setScale(2, RoundingMode.HALF_EVEN),
                    transaction.getCurrency(), assessment.score(), assessment.explanation());
            notificationService.notifyRole(Role.RISK_OFFICER, NotificationType.FRAUD_ALERT, assessment.riskLevel(), title, message,
                    "FRAUD_CHECK", check.getId());
            notificationService.notifyAreaManagers(agent.getArea().getId(), NotificationType.FRAUD_ALERT, assessment.riskLevel(),
                    title, message, "FRAUD_CHECK", check.getId());
            events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.RISK, "FRAUD_ALERT", Map.of(
                    "fraudCheckId", check.getId(), "transactionId", transaction.getId(), "reference", transaction.getReference(),
                    "agentCode", agent.getAgentCode(), "score", assessment.score(), "decision", assessment.decision(),
                    "riskLevel", assessment.riskLevel(), "reasonCodes", assessment.reasonCodes())));
        }
        return check;
    }

    private FraudAssessment fromAi(AiFraudResponse r) {
        double p;
        if (r.fraudProbability() != null) {
            p = Math.max(0, Math.min(1, r.fraudProbability()));
        } else if (r.riskScore() != null) {
            p = Math.max(0, Math.min(1, r.riskScore() / 100.0));
        } else {
            p = 0;
        }
        String action = r.recommendedAction() == null ? switch (RuleBasedFraudScorer.riskLevelFor(p)) {
            case CRITICAL -> "OPEN_INVESTIGATION";
            case HIGH -> "REVIEW_TRANSACTION";
            case MEDIUM -> "MONITOR";
            default -> "NO_ACTION";
        } : r.recommendedAction();
        return new FraudAssessment(BigDecimal.valueOf(p).setScale(4, RoundingMode.HALF_UP), RuleBasedFraudScorer.riskLevelFor(p),
                null, r.reasonCodes() == null ? List.of() : r.reasonCodes(),
                r.featureContributions() == null ? List.of() : r.featureContributions(),
                r.explanation() == null ? "No explanation supplied by the AI service" : r.explanation(),
                action, AiServiceClient.SOURCE_AI, r.modelVersion() == null ? "unknown" : r.modelVersion(), r.predictionId());
    }

    /** Response contract of POST /internal/ai/v1/fraud/evaluate (snake_case on the wire). */
    public record AiFraudResponse(Double fraudProbability, Integer riskScore, String riskLevel, String decision,
                                  List<String> reasonCodes, List<FeatureContribution> featureContributions,
                                  String explanation, String recommendedAction, String modelVersion, String predictionId) {
    }
}
