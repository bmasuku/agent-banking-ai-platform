package com.agentbanking.platform.fraud;

import com.agentbanking.platform.common.RiskLevel;
import com.agentbanking.platform.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedFraudScorerTest {

    private static FraudFeatures features(BigDecimal amount, int hour, boolean offHours, long tx1h, boolean nearThreshold,
                                          long nearThreshold1h, long customerTx24h, long customerAgents24h, String agentRisk) {
        return features(amount, hour, offHours, tx1h, nearThreshold, nearThreshold1h, customerTx24h, customerAgents24h,
                agentRisk, 0, 0, null);
    }

    private static FraudFeatures features(BigDecimal amount, int hour, boolean offHours, long tx1h, boolean nearThreshold,
                                          long nearThreshold1h, long customerTx24h, long customerAgents24h, String agentRisk,
                                          long priorFlagged, long confirmedFraud, BigDecimal distanceKm) {
        return new FraudFeatures(UUID.randomUUID(), "AGT-TEST", TransactionType.CASH_WITHDRAWAL, amount, hour, offHours,
                tx1h, tx1h * 3, 120, new BigDecimal("150.00"), new BigDecimal("60.00"), 0.5, 1.2,
                nearThreshold, nearThreshold1h, customerTx24h, customerAgents24h, false, agentRisk, 650, 0,
                priorFlagged, confirmedFraud, new BigDecimal("-17.8"), new BigDecimal("31.0"),
                null, null, distanceKm);
    }

    @Test
    void ordinaryDaytimeTransactionIsLowRiskWithExplanation() {
        FraudAssessment a = RuleBasedFraudScorer.score(features(new BigDecimal("120"), 11, false, 2, false, 0, 1, 1, "LOW"));
        assertThat(a.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(a.score()).isEqualByComparingTo("0.0200");
        assertThat(a.reasonCodes()).isEmpty();
        assertThat(a.explanation()).contains("No fraud typologies triggered");
        assertThat(a.recommendedAction()).isEqualTo("NO_ACTION");
        assertThat(a.decision()).isNull();
    }

    @Test
    void structuringAtNightCombinesSignalsIntoHighRisk() {
        FraudAssessment a = RuleBasedFraudScorer.score(features(new BigDecimal("499"), 2, true, 6, true, 4, 3, 1, "HIGH"));
        assertThat(a.reasonCodes()).contains("STRUCTURING_NEAR_THRESHOLD", "OFF_HOURS_ACTIVITY", "ELEVATED_VELOCITY",
                "REPEAT_NEAR_THRESHOLD_CUSTOMER", "AGENT_RISK_PROFILE");
        assertThat(a.score().doubleValue()).isGreaterThanOrEqualTo(0.60);
        assertThat(a.riskLevel()).isIn(RiskLevel.HIGH, RiskLevel.CRITICAL);
        assertThat(a.contributions()).isNotEmpty();
        assertThat(a.contributions().get(0).feature()).isEqualTo("nearThresholdCount1h");
        assertThat(a.explanation()).contains("structuring");
    }

    @Test
    void farLocationAndConfirmedFraudRaiseScore() {
        FraudAssessment a = RuleBasedFraudScorer.score(features(new BigDecimal("120"), 11, false, 1, false, 0, 1, 1, "LOW",
                0, 1, new BigDecimal("75.00")));
        assertThat(a.reasonCodes()).contains("LOCATION_FAR_FROM_REGISTERED", "CONFIRMED_FRAUD_HISTORY");
        assertThat(a.score().doubleValue()).isGreaterThan(0.5);
        assertThat(a.recommendedAction()).isIn("REVIEW_TRANSACTION", "OPEN_INVESTIGATION");
    }

    @Test
    void scoreIsMonotonicInAddedSignals() {
        double base = RuleBasedFraudScorer.score(features(new BigDecimal("300"), 10, false, 1, false, 0, 1, 1, "LOW")).score().doubleValue();
        double withNight = RuleBasedFraudScorer.score(features(new BigDecimal("300"), 23, true, 1, false, 0, 1, 1, "LOW")).score().doubleValue();
        double withHopping = RuleBasedFraudScorer.score(features(new BigDecimal("300"), 23, true, 1, false, 0, 1, 4, "LOW")).score().doubleValue();
        assertThat(withNight).isGreaterThan(base);
        assertThat(withHopping).isGreaterThan(withNight);
        assertThat(withHopping).isLessThan(1.0);
    }

    @Test
    void riskBandsFollowThresholds() {
        assertThat(RuleBasedFraudScorer.riskLevelFor(0.29)).isEqualTo(RiskLevel.LOW);
        assertThat(RuleBasedFraudScorer.riskLevelFor(0.30)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(RuleBasedFraudScorer.riskLevelFor(0.60)).isEqualTo(RiskLevel.HIGH);
        assertThat(RuleBasedFraudScorer.riskLevelFor(0.90)).isEqualTo(RiskLevel.CRITICAL);
    }
}
