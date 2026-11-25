package com.agentbanking.platform.fraud;

import com.agentbanking.platform.ai.FeatureContribution;
import com.agentbanking.platform.common.RiskLevel;

import java.math.BigDecimal;
import java.util.List;

/** Outcome of a fraud evaluation after the platform's decision policy has been applied. */
public record FraudAssessment(
        BigDecimal score,
        RiskLevel riskLevel,
        FraudDecision decision,
        List<String> reasonCodes,
        List<FeatureContribution> contributions,
        String explanation,
        String recommendedAction,
        String modelSource,
        String modelVersion,
        String aiPredictionId) {

    public FraudAssessment withDecision(FraudDecision newDecision, RiskLevel newRiskLevel) {
        return new FraudAssessment(score, newRiskLevel, newDecision, reasonCodes, contributions, explanation,
                recommendedAction, modelSource, modelVersion, aiPredictionId);
    }
}
