package com.agentbanking.platform.credit;

import com.agentbanking.platform.ai.AiServiceClient;
import com.agentbanking.platform.ai.FeatureContribution;
import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.common.RiskLevel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Exact Java port of the AI service credit scorecard (0-1000). Used when the AI service is unavailable.
 * Points are expert-set and mirrored by {@code app/credit/engine.py}; they are not fitted to defaults.
 */
public final class RuleBasedCreditScorer {

    public static final String MODEL_VERSION = "credit-scorecard:1.0.0";
    public static final int BASE_SCORE = 500;
    public static final int APPROVE_MIN_SCORE = 720;
    private static final BigDecimal LIMIT_STEP = BigDecimal.valueOf(50);

    private RuleBasedCreditScorer() {
    }

    public static CreditAssessment score(CreditFeatures f, CreditPolicy policy) {
        List<FeatureContribution> factors = scoreFactors(f);
        int raw = BASE_SCORE + factors.stream().mapToInt(c -> (int) c.contribution()).sum();
        int score = Math.max(0, Math.min(1000, raw));
        RiskLevel band = CreditPolicy.bandFor(score);

        BigDecimal limit = policy.limitFor(band, f.transactionVolume30d(), null);
        BigDecimal advance = Money.max(Money.ZERO, limit.subtract(f.outstandingPrincipal()));
        advance = floor50(advance);

        String status = f.agentStatus() == null ? "ACTIVE" : f.agentStatus().toUpperCase();
        List<String> rejections = new ArrayList<>();
        if ("FROZEN".equals(status) || "SUSPENDED".equals(status)) {
            rejections.add("Agent status " + status + " does not permit credit");
        }
        if (f.confirmedFraudCount() > 0) {
            rejections.add("Confirmed fraud on record");
        }
        if (f.advancesDefaulted() > 0) {
            rejections.add("Previous default on a float advance");
        }
        if (f.advancesOverdue() > 0) {
            rejections.add("An existing advance is overdue and must be repaid first");
        }
        if (band == RiskLevel.CRITICAL) {
            rejections.add("Credit score " + score + " is in the CRITICAL band (below 450)");
        }
        if (advance.signum() <= 0 && rejections.isEmpty()) {
            rejections.add("No available credit capacity after outstanding principal");
        }

        String decision;
        List<String> reasons;
        if (!rejections.isEmpty()) {
            decision = "REJECTED";
            reasons = rejections;
        } else {
            List<String> review = new ArrayList<>();
            if (score < APPROVE_MIN_SCORE) {
                review.add("Credit score " + score + " is below the automatic approval level of " + APPROVE_MIN_SCORE);
            }
            if (f.openInvestigations() > 0) {
                review.add("Open risk investigation");
            }
            if (!"ACTIVE".equals(status)) {
                review.add("Agent status is " + status);
            }
            if (f.flaggedTransactions30d() > 0) {
                review.add("Recent fraud alerts need human review");
            }
            if (f.requestedAmount() != null && f.requestedAmount().compareTo(advance) > 0) {
                review.add("Requested " + f.requestedAmount() + " exceeds the recommended advance of " + advance);
            }
            if (!review.isEmpty()) {
                decision = "MANUAL_REVIEW";
                reasons = review;
            } else {
                decision = "APPROVED";
                reasons = List.of("All scorecard eligibility conditions are met");
            }
        }

        List<FeatureContribution> ordered = factors.stream()
                .sorted(Comparator.comparingDouble((FeatureContribution c) -> Math.abs(c.contribution())).reversed())
                .toList();
        String positives = ordered.stream().filter(c -> c.contribution() > 0).limit(3)
                .map(FeatureContribution::description).collect(Collectors.joining("; "));
        String negatives = ordered.stream().filter(c -> c.contribution() < 0).limit(3)
                .map(FeatureContribution::description).collect(Collectors.joining("; "));
        String explanation = "Score " + score + "/1000 (" + band + " risk) from a base of " + BASE_SCORE + "."
                + (positives.isEmpty() ? "" : " Strengths: " + positives + ".")
                + (negatives.isEmpty() ? "" : " Concerns: " + negatives + ".")
                + " Recommendation: " + decision + " - " + String.join("; ", reasons) + ".";

        return new CreditAssessment(score, band, CreditPolicy.probabilityOfDefault(score), limit, advance,
                policy.dailyRateFor(band), !"REJECTED".equals(decision), decision, reasons, ordered, explanation,
                AiServiceClient.SOURCE_RULES, MODEL_VERSION, null);
    }

    static List<FeatureContribution> scoreFactors(CreditFeatures f) {
        List<FeatureContribution> factors = new ArrayList<>();
        if (f.tenureDays() < 30) {
            add(factors, "accountAgeDays", f.tenureDays(), -20, "New agent account (" + f.tenureDays() + " days old)");
        } else {
            add(factors, "accountAgeDays", f.tenureDays(), Math.min(80, jround(f.tenureDays() / 365.0 * 80)),
                    "Agent account is " + f.tenureDays() + " days old");
        }
        add(factors, "activeDays30d", f.activeDays30d(), jround(Math.min(30, f.activeDays30d()) / 30.0 * 80),
                "Active on " + f.activeDays30d() + " of the last 30 days");

        double volume = f.transactionVolume30d().doubleValue();
        add(factors, "transactionVolume30d", f.transactionVolume30d(),
                jround(clamp((Math.log10(volume + 1) - 2) * 40, 0, 100)),
                "30-day customer transaction volume of " + f.transactionVolume30d().toPlainString());

        add(factors, "avgDailyCommission30d", f.avgDailyCommission30d(),
                jround(Math.min(80.0, f.avgDailyCommission30d().doubleValue() * 8)),
                "Average commission earnings of " + f.avgDailyCommission30d().toPlainString() + " per day");

        if (f.activeDays30d() >= 5) {
            double cv = f.commissionVolatility();
            int points = cv < 0.5 ? 30 : cv < 1.0 ? 10 : -20;
            add(factors, "commissionVolatility", String.format("%.2f", cv), points,
                    points > 0 ? "Stable daily commission income" : "Highly variable daily commission income");
        }

        long onTime = Math.max(0, f.advancesRepaid() - f.advancesRepaidLate());
        add(factors, "advancesRepaidOnTime", onTime, (int) Math.min(100, onTime * 25),
                onTime + " float advance(s) repaid on time");
        add(factors, "advancesRepaidLate", f.advancesRepaidLate(), (int) -Math.min(120, f.advancesRepaidLate() * 40),
                f.advancesRepaidLate() + " advance(s) repaid after the due date");
        add(factors, "advancesDefaulted", f.advancesDefaulted(), (int) -Math.min(300, f.advancesDefaulted() * 150),
                f.advancesDefaulted() + " previous default(s) (more than 30 days past due)");
        add(factors, "advancesOverdue", f.advancesOverdue(), (int) -Math.min(200, f.advancesOverdue() * 100),
                f.advancesOverdue() + " advance(s) currently overdue");

        if (f.outstandingPrincipal().signum() > 0) {
            double leverage = f.outstandingPrincipal().doubleValue() / Math.max(1.0, volume / 30.0);
            int points = leverage > 5 ? -60 : leverage > 2 ? -30 : 0;
            add(factors, "outstandingPrincipal", f.outstandingPrincipal(), points,
                    String.format("Outstanding advance principal equals %.1f days of average transaction volume", leverage));
        }
        if (f.outstandingInterest().signum() > 0) {
            double coverDays = f.outstandingInterest().doubleValue() / Math.max(0.01, f.avgDailyCommission30d().doubleValue());
            int points = coverDays > 10 ? -40 : coverDays > 3 ? -15 : 0;
            add(factors, "outstandingInterest", f.outstandingInterest(), points,
                    String.format("Outstanding interest equals %.1f days of commission earnings", coverDays));
        }

        add(factors, "confirmedFraudCount", f.confirmedFraudCount(), (int) -Math.min(400, f.confirmedFraudCount() * 200),
                f.confirmedFraudCount() + " confirmed fraud case(s)");
        add(factors, "flaggedTransactions30d", f.flaggedTransactions30d(), (int) -Math.min(90, f.flaggedTransactions30d() * 15),
                f.flaggedTransactions30d() + " transaction(s) flagged by fraud monitoring in 30 days");
        add(factors, "openInvestigations", f.openInvestigations(), (int) -Math.min(160, f.openInvestigations() * 80),
                f.openInvestigations() + " open risk investigation(s)");

        String status = f.agentStatus() == null ? "ACTIVE" : f.agentStatus().toUpperCase();
        int statusPoints = "UNDER_REVIEW".equals(status) ? -60 : ("FROZEN".equals(status) || "SUSPENDED".equals(status)) ? -250 : 0;
        add(factors, "agentStatus", status, statusPoints, "Agent status is " + status);
        return factors;
    }

    private static BigDecimal floor50(BigDecimal amount) {
        if (amount.signum() <= 0) {
            return Money.ZERO;
        }
        return Money.of(amount.divide(LIMIT_STEP, 0, RoundingMode.DOWN).multiply(LIMIT_STEP));
    }

    private static int jround(double x) {
        return (int) Math.floor(x + 0.5);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static void add(List<FeatureContribution> factors, String feature, Object value, int points, String description) {
        if (points == 0) {
            return;
        }
        factors.add(new FeatureContribution(feature, String.valueOf(value), points, points > 0 ? "POSITIVE" : "NEGATIVE", description));
    }
}
