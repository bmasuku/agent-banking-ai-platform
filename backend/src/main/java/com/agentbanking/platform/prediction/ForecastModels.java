package com.agentbanking.platform.prediction;

import com.agentbanking.platform.ai.FeatureContribution;
import com.agentbanking.platform.prediction.FloatPrediction.TrajectoryPoint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ForecastModels {

    private ForecastModels() {
    }

    /** Net movement on the agent's balances caused by customer activity in one clock hour. */
    public record HourlyFlow(Instant hour, BigDecimal efloatNet, BigDecimal cashNet) {
    }

    /** Request contract for POST /internal/ai/v1/float/forecast (snake_case on the wire). */
    public record ForecastInput(UUID agentId, String agentCode, BigDecimal currentEfloat, BigDecimal currentCash,
                                BigDecimal minEfloatThreshold, BigDecimal minCashThreshold, int horizonHours, Instant asOf,
                                String timezone, int lookbackDays, List<HourlyFlow> history) {
    }

    public record ForecastResult(BigDecimal projectedEfloat, BigDecimal projectedCash, BigDecimal minProjectedEfloat,
                                 BigDecimal minProjectedCash, BigDecimal predictedEfloatNetFlow, BigDecimal predictedCashNetFlow,
                                 BigDecimal hoursToEfloatStockout, BigDecimal hoursToCashStockout, double confidence,
                                 List<TrajectoryPoint> trajectory, List<FeatureContribution> drivers, String explanation,
                                 String modelSource, String modelVersion) {
    }
}
