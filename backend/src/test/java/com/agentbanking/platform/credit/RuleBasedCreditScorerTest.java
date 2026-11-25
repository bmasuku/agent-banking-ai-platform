package com.agentbanking.platform.credit;

import com.agentbanking.platform.common.RiskLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedCreditScorerTest {

    private final CreditPolicy policy = new CreditPolicy(new BigDecimal("5000"), new BigDecimal("2500"), new BigDecimal("1000"),
            new BigDecimal("0.0010"), new BigDecimal("0.0015"), new BigDecimal("0.0020"), new BigDecimal("0.0030"));

    private static CreditFeatures features(long tenure, long activeDays, String volume, long repaid, long late, long overdue,
                                           long defaulted, String outstanding, long flagged, long fraud, long investigations,
                                           String status) {
        return new CreditFeatures(UUID.randomUUID(), "AGT-TEST", tenure, activeDays, activeDays * 12, new BigDecimal(volume),
                new BigDecimal("12.00"), 0.3, new BigDecimal("2000"), new BigDecimal("1500"), new BigDecimal(outstanding),
                BigDecimal.ZERO, repaid, late, overdue, defaulted, flagged, fraud, investigations, status, null);
    }

    @Test
    void establishedReliableAgentScoresLowRiskWithApproval() {
        CreditAssessment a = RuleBasedCreditScorer.score(
                features(400, 29, "60000", 3, 0, 0, 0, "0", 0, 0, 0, "ACTIVE"), policy);
        assertThat(a.score()).isGreaterThanOrEqualTo(720);
        assertThat(a.riskBand()).isEqualTo(RiskLevel.LOW);
        assertThat(a.recommendedLimit()).isEqualByComparingTo("5000.00");
        assertThat(a.recommendation()).isEqualTo("APPROVED");
        assertThat(a.eligible()).isTrue();
        assertThat(a.explanation()).contains("Strengths");
        assertThat(a.factors()).extracting("feature").contains("accountAgeDays", "advancesRepaidOnTime");
    }

    @Test
    void overdueFlaggedFrozenAgentIsRejected() {
        CreditAssessment a = RuleBasedCreditScorer.score(
                features(60, 10, "8000", 0, 1, 2, 1, "3000", 6, 1, 1, "FROZEN"), policy);
        assertThat(a.recommendation()).isEqualTo("REJECTED");
        assertThat(a.eligible()).isFalse();
        assertThat(a.recommendedLimit()).isEqualByComparingTo("0.00");
        assertThat(a.explanation()).contains("Concerns");
        assertThat(a.probabilityOfDefault().doubleValue()).isGreaterThan(0.5);
    }

    @Test
    void limitIsBoundedByVolumeCapacityAndRoundedDown() {
        assertThat(policy.limitFor(RiskLevel.LOW, new BigDecimal("3030"), null)).isEqualByComparingTo("750.00");
        assertThat(policy.limitFor(RiskLevel.MEDIUM, new BigDecimal("100000"), new BigDecimal("1234"))).isEqualByComparingTo("1200.00");
        assertThat(policy.limitFor(RiskLevel.CRITICAL, new BigDecimal("100000"), null)).isEqualByComparingTo("0.00");
    }

    @Test
    void bandsAndDefaultProbabilityAreMonotonic() {
        assertThat(CreditPolicy.bandFor(750)).isEqualTo(RiskLevel.LOW);
        assertThat(CreditPolicy.bandFor(749)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(CreditPolicy.bandFor(600)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(CreditPolicy.bandFor(450)).isEqualTo(RiskLevel.HIGH);
        assertThat(CreditPolicy.bandFor(449)).isEqualTo(RiskLevel.CRITICAL);
        assertThat(CreditPolicy.probabilityOfDefault(500)).isGreaterThan(CreditPolicy.probabilityOfDefault(700));
    }
}
