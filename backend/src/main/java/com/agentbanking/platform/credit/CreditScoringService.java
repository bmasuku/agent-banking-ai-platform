package com.agentbanking.platform.credit;

import com.agentbanking.platform.admin.SettingKey;
import com.agentbanking.platform.admin.SystemSettingService;
import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.agent.AgentRepository;
import com.agentbanking.platform.agent.AgentTier;
import com.agentbanking.platform.ai.AiServiceClient;
import com.agentbanking.platform.ai.FeatureContribution;
import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.RiskLevel;
import com.agentbanking.platform.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Credit scoring integration. Features come from the ledger and operational history, the score from
 * the AI service (or the explainable scorecard fallback), and limits/pricing from platform policy.
 * The AI call is made outside any database transaction.
 */
@Service
@RequiredArgsConstructor
public class CreditScoringService {

    private final CreditFeatureService featureService;
    private final CreditScoreRepository repository;
    private final AgentRepository agentRepository;
    private final AiServiceClient aiClient;
    private final SystemSettingService settings;
    private final TransactionTemplate transactionTemplate;

    public CreditPolicy currentPolicy() {
        return new CreditPolicy(
                settings.getDecimal(SettingKey.CREDIT_LIMIT_CAP_LOW), settings.getDecimal(SettingKey.CREDIT_LIMIT_CAP_MEDIUM),
                settings.getDecimal(SettingKey.CREDIT_LIMIT_CAP_HIGH),
                settings.getDecimal(SettingKey.CREDIT_DAILY_RATE_LOW), settings.getDecimal(SettingKey.CREDIT_DAILY_RATE_MEDIUM),
                settings.getDecimal(SettingKey.CREDIT_DAILY_RATE_HIGH), settings.getDecimal(SettingKey.CREDIT_DAILY_RATE_CRITICAL));
    }

    /** Scores the agent, persists the result and refreshes the agent's score, risk level, tier and limit. */
    public CreditScoreResponse evaluateAndStore(UUID agentId, BigDecimal requestedAmount) {
        CreditFeatures features = featureService.build(agentId, requestedAmount);
        CreditAssessment assessment = assess(features);
        return transactionTemplate.execute(status -> store(agentId, features, assessment));
    }

    /**
     * Persists a demonstration credit profile after historical activity has been seeded. Used only by
     * {@code DemoDataSeeder} so presenters see the published persona scores (850 / 760 / 650 / 420 / 280).
     */
    public CreditScoreResponse applyDemoProfile(UUID agentId, int score, String explanation,
                                                List<FeatureContribution> factors, String recommendation,
                                                boolean eligible) {
        CreditFeatures features = featureService.build(agentId, null);
        CreditPolicy policy = currentPolicy();
        int clamped = Math.max(0, Math.min(1000, score));
        RiskLevel band = CreditPolicy.bandFor(clamped);
        CreditAssessment assessment = new CreditAssessment(
                clamped, band, CreditPolicy.probabilityOfDefault(clamped),
                policy.limitFor(band, features.transactionVolume30d(), null),
                Money.ZERO, policy.dailyRateFor(band), eligible, recommendation,
                List.of(explanation), factors == null ? List.of() : factors, explanation,
                "DEMO_SEED", "demo-personas-1", null);
        return transactionTemplate.execute(status -> store(agentId, features, assessment));
    }

    public CreditAssessment assess(CreditFeatures features) {
        CreditPolicy policy = currentPolicy();
        return aiClient.post("/internal/ai/v1/credit/score", features, AiCreditResponse.class)
                .filter(r -> r.creditScore() != null)
                .map(r -> fromAi(r, features, policy))
                .orElseGet(() -> RuleBasedCreditScorer.score(features, policy));
    }

    @Transactional(readOnly = true)
    public CreditScoreResponse latest(UUID agentId) {
        return repository.findTopByAgent_IdOrderByCreatedAtDesc(agentId).map(CreditScoreResponse::from)
                .orElseThrow(() -> BusinessException.notFound("Credit score for agent", agentId));
    }

    @Transactional(readOnly = true)
    public PageResponse<CreditScoreResponse> history(UUID agentId, Pageable pageable) {
        return PageResponse.of(repository.findByAgent_IdOrderByCreatedAtDesc(agentId, pageable), CreditScoreResponse::from);
    }

    private CreditScoreResponse store(UUID agentId, CreditFeatures features, CreditAssessment a) {
        Agent agent = agentRepository.findById(agentId).orElseThrow(() -> BusinessException.notFound("Agent", agentId));
        CreditScore score = new CreditScore();
        score.setAgent(agent);
        score.setScore(a.score());
        score.setRiskBand(a.riskBand());
        score.setProbabilityOfDefault(a.probabilityOfDefault());
        score.setRecommendedLimit(a.recommendedLimit());
        score.setRecommendedDailyRate(a.recommendedDailyRate());
        score.setFactors(a.factors());
        score.setFeatures(features.toMap());
        score.setExplanation(a.explanation());
        score.setModelSource(a.modelSource());
        score.setModelVersion(a.modelVersion());
        score.setRecommendation(a.recommendation());
        score.setEligible(a.eligible());
        score.setAiPredictionId(a.aiPredictionId());
        repository.save(score);

        agent.setCreditScore(a.score());
        agent.setCreditLimit(a.recommendedLimit());
        agent.setRiskLevel(a.riskBand());
        agent.setTier(AgentTier.fromRisk(a.riskBand()));
        return CreditScoreResponse.from(score);
    }

    private CreditAssessment fromAi(AiCreditResponse r, CreditFeatures features, CreditPolicy policy) {
        int score = Math.max(0, Math.min(1000, r.creditScore()));
        RiskLevel band = CreditPolicy.bandFor(score);
        BigDecimal pd = r.probabilityOfDefault() != null
                ? BigDecimal.valueOf(r.probabilityOfDefault()).setScale(4, RoundingMode.HALF_UP)
                : CreditPolicy.probabilityOfDefault(score);
        BigDecimal suggestion = r.recommendedLimit() == null ? null : Money.of(r.recommendedLimit());
        BigDecimal limit = policy.limitFor(band, features.transactionVolume30d(), suggestion);
        BigDecimal advance = r.recommendedAdvanceAmount() == null
                ? Money.max(Money.ZERO, limit.subtract(features.outstandingPrincipal()))
                : Money.min(limit, Money.of(r.recommendedAdvanceAmount()));
        String recommendation = r.decision() == null ? "MANUAL_REVIEW" : r.decision();
        boolean eligible = r.eligible() == null ? !"REJECTED".equals(recommendation) : r.eligible();
        List<String> reasons = r.decisionReasons() == null ? List.of() : r.decisionReasons();
        return new CreditAssessment(score, band, pd, limit, advance, policy.dailyRateFor(band), eligible, recommendation,
                reasons, r.factors() == null ? List.of() : r.factors(),
                r.explanation() == null ? "No explanation supplied by the AI service" : r.explanation(),
                AiServiceClient.SOURCE_AI, r.modelVersion() == null ? "unknown" : r.modelVersion(), r.predictionId());
    }

    /** Response contract of POST /internal/ai/v1/credit/score (snake_case on the wire). */
    public record AiCreditResponse(Integer creditScore, Double probabilityOfDefault, Double recommendedLimit,
                                   Double recommendedAdvanceAmount, Boolean eligible, String decision,
                                   List<String> decisionReasons, List<FeatureContribution> factors, String explanation,
                                   String modelVersion, String predictionId) {
    }

    public record CreditScoreResponse(UUID id, UUID agentId, String agentCode, int score, RiskLevel riskBand,
                                      BigDecimal probabilityOfDefault, BigDecimal recommendedLimit,
                                      BigDecimal recommendedAdvanceAmount, BigDecimal recommendedDailyRate,
                                      boolean eligible, String recommendation, List<String> decisionReasons,
                                      List<FeatureContribution> factors, Map<String, Object> features,
                                      String explanation, String modelSource, String modelVersion,
                                      String aiPredictionId, Instant createdAt) {
        public static CreditScoreResponse from(CreditScore s) {
            BigDecimal advance = Money.max(Money.ZERO, Money.of(s.getRecommendedLimit())
                    .subtract(principalFromFeatures(s.getFeatures())));
            return new CreditScoreResponse(s.getId(), s.getAgent().getId(), s.getAgent().getAgentCode(), s.getScore(),
                    s.getRiskBand(), s.getProbabilityOfDefault(), Money.of(s.getRecommendedLimit()), advance,
                    s.getRecommendedDailyRate(), Boolean.TRUE.equals(s.getEligible()), s.getRecommendation(),
                    List.of(), s.getFactors(), s.getFeatures(), s.getExplanation(), s.getModelSource(),
                    s.getModelVersion(), s.getAiPredictionId(), s.getCreatedAt());
        }

        private static BigDecimal principalFromFeatures(Map<String, Object> features) {
            if (features == null || features.get("outstandingPrincipal") == null) {
                return Money.ZERO;
            }
            return Money.of(new BigDecimal(features.get("outstandingPrincipal").toString()));
        }
    }
}
