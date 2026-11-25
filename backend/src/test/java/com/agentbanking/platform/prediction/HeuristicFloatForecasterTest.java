package com.agentbanking.platform.prediction;

import com.agentbanking.platform.ai.AiServiceClient;
import com.agentbanking.platform.prediction.ForecastModels.ForecastInput;
import com.agentbanking.platform.prediction.ForecastModels.ForecastResult;
import com.agentbanking.platform.prediction.ForecastModels.HourlyFlow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicFloatForecasterTest {

    private static final ZoneId ZONE = ZoneId.of("Africa/Harare");
    private static final Instant AS_OF = LocalDate.of(2026, 11, 25).atTime(6, 30).atZone(ZONE).toInstant();

    private static ForecastInput input(String efloat, String cash, List<HourlyFlow> history) {
        return new ForecastInput(UUID.randomUUID(), "AGT-TEST", new BigDecimal(efloat), new BigDecimal(cash),
                new BigDecimal("500"), new BigDecimal("300"), 24, AS_OF, ZONE.getId(), 14, history);
    }

    /** Each previous day: deposits drain 100 e-float per hour from 08:00 to 17:00 and add the same in cash. */
    private static List<HourlyFlow> depositHeavyHistory(int days) {
        List<HourlyFlow> flows = new ArrayList<>();
        for (int d = 1; d <= days; d++) {
            LocalDate day = LocalDate.of(2026, 11, 25).minusDays(d);
            for (int h = 8; h < 18; h++) {
                Instant hour = day.atTime(h, 0).atZone(ZONE).toInstant();
                flows.add(new HourlyFlow(hour, new BigDecimal("-100"), new BigDecimal("100")));
            }
        }
        return flows;
    }

    @Test
    void projectsSeasonalDrainAndStockout() {
        ForecastResult r = HeuristicFloatForecaster.forecast(input("1200", "400", depositHeavyHistory(7)));
        assertThat(r.predictedEfloatNetFlow()).isEqualByComparingTo("-1000.00");
        assertThat(r.predictedCashNetFlow()).isEqualByComparingTo("1000.00");
        assertThat(r.projectedEfloat()).isEqualByComparingTo("200.00");
        assertThat(r.minProjectedEfloat()).isEqualByComparingTo("200.00");
        assertThat(r.hoursToEfloatStockout()).isNotNull();
        assertThat(r.hoursToEfloatStockout().doubleValue()).isBetween(1.0, 24.0);
        assertThat(r.hoursToCashStockout()).isNull();
        assertThat(r.trajectory()).hasSize(24);
        assertThat(r.explanation()).contains("E-float is projected to fall below");
        assertThat(r.drivers()).extracting("feature").contains("predictedEfloatNetFlow", "peakEfloatOutflowHour", "historyDays");
        assertThat(r.modelSource()).isEqualTo(AiServiceClient.SOURCE_RULES);
        assertThat(r.confidence()).isGreaterThan(0.5);
    }

    @Test
    void identicalDaysProduceZeroUncertaintyBand() {
        ForecastResult r = HeuristicFloatForecaster.forecast(input("5000", "5000", depositHeavyHistory(5)));
        r.trajectory().forEach(p -> assertThat(p.efloatLower()).isEqualByComparingTo(p.efloat()));
    }

    @Test
    void noHistoryKeepsBalancesFlatWithLowConfidence() {
        ForecastResult r = HeuristicFloatForecaster.forecast(input("1000", "1000", List.of()));
        assertThat(r.projectedEfloat()).isEqualByComparingTo("1000.00");
        assertThat(r.projectedCash()).isEqualByComparingTo("1000.00");
        assertThat(r.confidence()).isEqualTo(0.2);
        assertThat(r.hoursToEfloatStockout()).isNull();
    }

    @Test
    void reportsImmediateStockoutWhenAlreadyBelowMinimum() {
        ForecastResult r = HeuristicFloatForecaster.forecast(input("100", "1000", List.of()));
        assertThat(r.hoursToEfloatStockout()).isEqualByComparingTo("0.00");
        assertThat(r.explanation()).contains("already");
    }
}
