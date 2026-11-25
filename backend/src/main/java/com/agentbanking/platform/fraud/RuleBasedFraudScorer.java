package com.agentbanking.platform.fraud;

import com.agentbanking.platform.ai.AiServiceClient;
import com.agentbanking.platform.ai.FeatureContribution;
import com.agentbanking.platform.common.RiskLevel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Transparent typology-based fraud model used when the AI service is unavailable. Each triggered
 * typology contributes an independent probability w_i; the combined score is 1 - prod(1 - w_i).
 */
public final class RuleBasedFraudScorer {

    public static final String MODEL_VERSION = "fraud-typology-rules-v1";
    private static final double BASELINE = 0.02;

    private RuleBasedFraudScorer() {
    }

    public static FraudAssessment score(FraudFeatures f) {
        List<Signal> signals = new ArrayList<>();

        if (f.nearReportingThreshold() && f.nearThresholdCount1h() >= 2) {
            signals.add(new Signal("STRUCTURING_NEAR_THRESHOLD", 0.45, "nearThresholdCount1h", String.valueOf(f.nearThresholdCount1h()),
                    f.nearThresholdCount1h() + " transactions just below a reporting threshold in the last hour (possible structuring)"));
        } else if (f.nearReportingThreshold()) {
            signals.add(new Signal("NEAR_REPORTING_THRESHOLD", 0.12, "amount", f.amount().toPlainString(),
                    "Amount sits just below a reporting threshold"));
        }
        if (f.offHours()) {
            double w = f.amountToAvgRatio() > 2.0 ? 0.25 : 0.15;
            signals.add(new Signal("OFF_HOURS_ACTIVITY", w, "hourOfDay", String.valueOf(f.hourOfDay()),
                    "Transaction at " + f.hourOfDay() + ":00, outside normal trading hours (06:00-21:59)"));
        }
        if (f.agentTxCount1h() >= 8) {
            signals.add(new Signal("VELOCITY_SPIKE", 0.30, "agentTxCount1h", String.valueOf(f.agentTxCount1h()),
                    f.agentTxCount1h() + " agent transactions in the last hour"));
        } else if (f.agentTxCount1h() >= 5) {
            signals.add(new Signal("ELEVATED_VELOCITY", 0.15, "agentTxCount1h", String.valueOf(f.agentTxCount1h()),
                    f.agentTxCount1h() + " agent transactions in the last hour"));
        }
        if (f.agentHistoryCount30d() >= 10 && f.amountZScore() >= 4.0) {
            signals.add(new Signal("EXTREME_AMOUNT_DEVIATION", 0.35, "amountZScore", fmt(f.amountZScore()),
                    "Amount is " + fmt(f.amountZScore()) + " standard deviations above the agent's 30-day mean of " + f.agentAvgAmount30d()));
        } else if (f.agentHistoryCount30d() >= 10 && f.amountZScore() >= 2.5) {
            signals.add(new Signal("AMOUNT_DEVIATION", 0.15, "amountZScore", fmt(f.amountZScore()),
                    "Amount is " + fmt(f.amountZScore()) + " standard deviations above the agent's 30-day mean"));
        }
        if (f.customerTxCount24h() >= 4) {
            signals.add(new Signal("RAPID_CUSTOMER_REPEAT", 0.30, "customerTxCount24h", String.valueOf(f.customerTxCount24h()),
                    "Customer already transacted " + f.customerTxCount24h() + " times in 24 hours"));
        } else if (f.customerTxCount24h() >= 2 && f.nearReportingThreshold()) {
            signals.add(new Signal("REPEAT_NEAR_THRESHOLD_CUSTOMER", 0.20, "customerTxCount24h", String.valueOf(f.customerTxCount24h()),
                    "Same customer repeating near-threshold amounts"));
        }
        if (f.customerDistinctAgents24h() >= 3) {
            signals.add(new Signal("MULTI_AGENT_HOPPING", 0.25, "customerDistinctAgents24h", String.valueOf(f.customerDistinctAgents24h()),
                    "Customer used " + f.customerDistinctAgents24h() + " different agents in 24 hours"));
        }
        if ("CRITICAL".equals(f.agentRiskLevel())) {
            signals.add(new Signal("AGENT_RISK_PROFILE", 0.20, "agentRiskLevel", f.agentRiskLevel(), "Agent currently rated CRITICAL risk"));
        } else if ("HIGH".equals(f.agentRiskLevel())) {
            signals.add(new Signal("AGENT_RISK_PROFILE", 0.10, "agentRiskLevel", f.agentRiskLevel(), "Agent currently rated HIGH risk"));
        }
        if (f.agentOpenFlags() >= 3) {
            signals.add(new Signal("OPEN_ALERT_BACKLOG", 0.10, "agentOpenFlags", String.valueOf(f.agentOpenFlags()),
                    f.agentOpenFlags() + " unresolved fraud alerts on this agent"));
        }
        if (f.priorFlagged90d() >= 3) {
            signals.add(new Signal("SUSPICIOUS_HISTORY", 0.18, "priorFlagged90d", String.valueOf(f.priorFlagged90d()),
                    f.priorFlagged90d() + " flagged transactions in the last 90 days"));
        } else if (f.priorFlagged90d() >= 1 || f.agentOpenFlags() >= 1) {
            signals.add(new Signal("PRIOR_ALERTS", 0.08, "priorFlagged90d",
                    String.valueOf(Math.max(f.priorFlagged90d(), f.agentOpenFlags())),
                    "Earlier fraud alerts on record for this agent"));
        }
        if (f.confirmedFraudCount() >= 1) {
            signals.add(new Signal("CONFIRMED_FRAUD_HISTORY", 0.40, "confirmedFraudCount", String.valueOf(f.confirmedFraudCount()),
                    f.confirmedFraudCount() + " previously confirmed fraud case(s) linked to this agent"));
        }
        if (f.distanceFromRegisteredKm() != null) {
            double km = f.distanceFromRegisteredKm().doubleValue();
            if (km > 50) {
                signals.add(new Signal("LOCATION_FAR_FROM_REGISTERED", 0.35, "distanceFromRegisteredKm", fmt(km),
                        "Transaction captured " + fmt(km) + " km from the agent's registered location"));
            } else if (km > 10) {
                signals.add(new Signal("LOCATION_AWAY_FROM_REGISTERED", 0.15, "distanceFromRegisteredKm", fmt(km),
                        "Transaction captured " + fmt(km) + " km from the agent's registered location"));
            }
        }

        double notFraud = 1.0 - BASELINE;
        for (Signal s : signals) {
            notFraud *= (1.0 - s.weight);
        }
        double probability = Math.min(0.999, 1.0 - notFraud);

        List<FeatureContribution> contributions = signals.stream()
                .sorted(Comparator.comparingDouble(Signal::weight).reversed())
                .map(s -> new FeatureContribution(s.feature, s.value, s.weight, "INCREASES_RISK", s.description))
                .toList();
        String explanation = signals.isEmpty()
                ? "No fraud typologies triggered; activity is consistent with the agent's recent behaviour."
                : signals.stream().sorted(Comparator.comparingDouble(Signal::weight).reversed())
                .map(Signal::description).collect(Collectors.joining("; ")) + ".";

        RiskLevel level = riskLevelFor(probability);
        String action = switch (level) {
            case CRITICAL -> "OPEN_INVESTIGATION";
            case HIGH -> "REVIEW_TRANSACTION";
            case MEDIUM -> "MONITOR";
            default -> "NO_ACTION";
        };
        return new FraudAssessment(BigDecimal.valueOf(probability).setScale(4, RoundingMode.HALF_UP), level,
                null, signals.stream().map(Signal::code).distinct().toList(), contributions, explanation, action,
                AiServiceClient.SOURCE_RULES, MODEL_VERSION, null);
    }

    public static RiskLevel riskLevelFor(double probability) {
        if (probability >= 0.90) {
            return RiskLevel.CRITICAL;
        }
        if (probability >= 0.60) {
            return RiskLevel.HIGH;
        }
        if (probability >= 0.30) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private static String fmt(double v) {
        return String.format("%.2f", v);
    }

    private record Signal(String code, double weight, String feature, String value, String description) {
    }
}
