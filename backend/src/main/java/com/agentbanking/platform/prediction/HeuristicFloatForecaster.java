package com.agentbanking.platform.prediction;

import com.agentbanking.platform.ai.AiServiceClient;
import com.agentbanking.platform.ai.FeatureContribution;
import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.prediction.FloatPrediction.TrajectoryPoint;
import com.agentbanking.platform.prediction.ForecastModels.ForecastInput;
import com.agentbanking.platform.prediction.ForecastModels.ForecastResult;
import com.agentbanking.platform.prediction.ForecastModels.HourlyFlow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Explainable fallback forecaster: recency-weighted hour-of-day seasonal means of customer-driven
 * net flows, rolled forward from current balances, with an 80% uncertainty band from the weighted
 * hourly variance.
 */
public final class HeuristicFloatForecaster {

    public static final String MODEL_VERSION = "seasonal-hourly-heuristic-v1";
    private static final double DECAY = 0.85;
    private static final double Z80 = 1.2816;

    private HeuristicFloatForecaster() {
    }

    public static ForecastResult forecast(ForecastInput in) {
        ZoneId zone = ZoneId.of(in.timezone());
        ZonedDateTime asOf = in.asOf().atZone(zone);
        LocalDate today = asOf.toLocalDate();

        Map<LocalDate, double[][]> byDay = new HashMap<>();
        LocalDate earliest = null;
        for (HourlyFlow f : in.history()) {
            ZonedDateTime t = f.hour().atZone(zone);
            LocalDate d = t.toLocalDate();
            if (!d.isBefore(today)) {
                continue;
            }
            double[][] day = byDay.computeIfAbsent(d, k -> new double[24][2]);
            day[t.getHour()][0] += f.efloatNet().doubleValue();
            day[t.getHour()][1] += f.cashNet().doubleValue();
            earliest = earliest == null || d.isBefore(earliest) ? d : earliest;
        }
        int observedDays = earliest == null ? 0 : (int) Math.min(in.lookbackDays(), ChronoUnit.DAYS.between(earliest, today));

        double[] meanE = new double[24];
        double[] meanC = new double[24];
        double[] varE = new double[24];
        double[] varC = new double[24];
        if (observedDays > 0) {
            for (int h = 0; h < 24; h++) {
                double wSum = 0;
                double sE = 0;
                double sC = 0;
                for (int d = 1; d <= observedDays; d++) {
                    double w = Math.pow(DECAY, d - 1);
                    double[][] day = byDay.get(today.minusDays(d));
                    double e = day == null ? 0 : day[h][0];
                    double c = day == null ? 0 : day[h][1];
                    wSum += w;
                    sE += w * e;
                    sC += w * c;
                }
                meanE[h] = sE / wSum;
                meanC[h] = sC / wSum;
                double vE = 0;
                double vC = 0;
                for (int d = 1; d <= observedDays; d++) {
                    double w = Math.pow(DECAY, d - 1);
                    double[][] day = byDay.get(today.minusDays(d));
                    double e = day == null ? 0 : day[h][0];
                    double c = day == null ? 0 : day[h][1];
                    vE += w * Math.pow(e - meanE[h], 2);
                    vC += w * Math.pow(c - meanC[h], 2);
                }
                varE[h] = vE / wSum;
                varC[h] = vC / wSum;
            }
        }

        double efloat = in.currentEfloat().doubleValue();
        double cash = in.currentCash().doubleValue();
        double minE = efloat;
        double minC = cash;
        double cumVarE = 0;
        double cumVarC = 0;
        Double stockoutE = efloat < in.minEfloatThreshold().doubleValue() ? 0.0 : null;
        Double stockoutC = cash < in.minCashThreshold().doubleValue() ? 0.0 : null;
        Instant hourStart = in.asOf().truncatedTo(ChronoUnit.HOURS);
        List<TrajectoryPoint> trajectory = new ArrayList<>();
        double worstEHourFlow = 0;
        int worstEHour = -1;
        double worstCHourFlow = 0;
        int worstCHour = -1;
        for (int t = 1; t <= in.horizonHours(); t++) {
            Instant point = hourStart.plus(Duration.ofHours(t));
            int hour = point.minus(Duration.ofHours(1)).atZone(zone).getHour();
            efloat += meanE[hour];
            cash += meanC[hour];
            cumVarE += varE[hour];
            cumVarC += varC[hour];
            minE = Math.min(minE, efloat);
            minC = Math.min(minC, cash);
            if (stockoutE == null && efloat < in.minEfloatThreshold().doubleValue()) {
                stockoutE = (double) t;
            }
            if (stockoutC == null && cash < in.minCashThreshold().doubleValue()) {
                stockoutC = (double) t;
            }
            if (meanE[hour] < worstEHourFlow) {
                worstEHourFlow = meanE[hour];
                worstEHour = hour;
            }
            if (meanC[hour] < worstCHourFlow) {
                worstCHourFlow = meanC[hour];
                worstCHour = hour;
            }
            double bandE = Z80 * Math.sqrt(cumVarE);
            double bandC = Z80 * Math.sqrt(cumVarC);
            trajectory.add(new TrajectoryPoint(point, money(efloat), money(efloat - bandE), money(efloat + bandE),
                    money(cash), money(cash - bandC), money(cash + bandC)));
        }

        BigDecimal netE = money(efloat - in.currentEfloat().doubleValue());
        BigDecimal netC = money(cash - in.currentCash().doubleValue());
        double confidence = observedDays == 0 ? 0.2 : Math.min(0.9, 0.35 + 0.04 * observedDays);

        List<FeatureContribution> drivers = new ArrayList<>();
        drivers.add(new FeatureContribution("predictedEfloatNetFlow", netE.toPlainString(), netE.doubleValue(),
                netE.signum() < 0 ? "DRAINS_EFLOAT" : "BUILDS_EFLOAT",
                "Expected net e-float movement over the next " + in.horizonHours() + "h based on the same hours on previous days"));
        drivers.add(new FeatureContribution("predictedCashNetFlow", netC.toPlainString(), netC.doubleValue(),
                netC.signum() < 0 ? "DRAINS_CASH" : "BUILDS_CASH",
                "Expected net cash movement over the next " + in.horizonHours() + "h"));
        if (worstEHour >= 0) {
            drivers.add(new FeatureContribution("peakEfloatOutflowHour", String.format("%02d:00", worstEHour), worstEHourFlow,
                    "DRAINS_EFLOAT", String.format("Heaviest e-float outflow typically around %02d:00 (%.2f per hour, mostly deposits)",
                    worstEHour, worstEHourFlow)));
        }
        if (worstCHour >= 0) {
            drivers.add(new FeatureContribution("peakCashOutflowHour", String.format("%02d:00", worstCHour), worstCHourFlow,
                    "DRAINS_CASH", String.format("Heaviest cash outflow typically around %02d:00 (%.2f per hour, mostly withdrawals)",
                    worstCHour, worstCHourFlow)));
        }
        drivers.add(new FeatureContribution("historyDays", String.valueOf(observedDays), observedDays, "CONFIDENCE",
                observedDays + " day(s) of hourly history used"));

        StringBuilder explanation = new StringBuilder();
        explanation.append(String.format("Over the next %dh e-float is expected to move by %s (to %s) and cash by %s (to %s), "
                        + "based on recency-weighted hour-of-day patterns from %d day(s) of history.",
                in.horizonHours(), netE, money(efloat), netC, money(cash), observedDays));
        if (stockoutE != null) {
            explanation.append(String.format(" E-float is projected to fall below the minimum operating level of %s %s.",
                    in.minEfloatThreshold(), stockoutE == 0 ? "already" : "in about " + stockoutE.intValue() + "h"));
        }
        if (stockoutC != null) {
            explanation.append(String.format(" Cash is projected to fall below the minimum operating level of %s %s.",
                    in.minCashThreshold(), stockoutC == 0 ? "already" : "in about " + stockoutC.intValue() + "h"));
        }

        return new ForecastResult(money(efloat), money(cash), money(minE), money(minC), netE, netC,
                stockoutE == null ? null : BigDecimal.valueOf(stockoutE).setScale(2, RoundingMode.HALF_UP),
                stockoutC == null ? null : BigDecimal.valueOf(stockoutC).setScale(2, RoundingMode.HALF_UP),
                confidence, trajectory, drivers, explanation.toString(), AiServiceClient.SOURCE_RULES, MODEL_VERSION);
    }

    private static BigDecimal money(double v) {
        return Money.of(BigDecimal.valueOf(v));
    }
}
