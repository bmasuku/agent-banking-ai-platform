package com.agentbanking.platform.credit;

import com.agentbanking.platform.ai.FeatureContribution;
import com.agentbanking.platform.common.RiskLevel;

import java.math.BigDecimal;
import java.util.List;

/** Credit evaluation after platform policy (band caps and pricing) has been applied. */
public record CreditAssessment(
        int score,
        RiskLevel riskBand,
        BigDecimal probabilityOfDefault,
        BigDecimal recommendedLimit,
        BigDecimal recommendedAdvanceAmount,
        BigDecimal recommendedDailyRate,
        boolean eligible,
        String recommendation,
        List<String> decisionReasons,
        List<FeatureContribution> factors,
        String explanation,
        String modelSource,
        String modelVersion,
        String aiPredictionId) {
}
