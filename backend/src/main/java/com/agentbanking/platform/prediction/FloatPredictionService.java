package com.agentbanking.platform.prediction;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.agent.AgentRepository;
import com.agentbanking.platform.ai.AiServiceClient;
import com.agentbanking.platform.ai.FeatureContribution;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.ledger.BalanceSnapshot;
import com.agentbanking.platform.ledger.LedgerAccountService;
import com.agentbanking.platform.ledger.LedgerEntryRepository;
import com.agentbanking.platform.prediction.FloatPrediction.TrajectoryPoint;
import com.agentbanking.platform.prediction.ForecastModels.ForecastInput;
import com.agentbanking.platform.prediction.ForecastModels.ForecastResult;
import com.agentbanking.platform.prediction.ForecastModels.HourlyFlow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Float demand forecasting. History is read from the ledger, the forecast comes from the AI service or
 * the heuristic fallback, and the platform derives risk classification and top-up recommendations.
 */
@Service
@RequiredArgsConstructor
public class FloatPredictionService {

    public static final int DEFAULT_HORIZON_HOURS = 24;
    private static final int LOOKBACK_DAYS = 14;
    private static final BigDecimal TOPUP_STEP = BigDecimal.valueOf(50);

    private final AgentRepository agentRepository;
    private final LedgerAccountService accounts;
    private final LedgerEntryRepository entryRepository;
    private final FloatPredictionRepository repository;
    private final AiServiceClient aiClient;
    private final TransactionTemplate transactionTemplate;

    public PredictionResponse predict(UUID agentId, int horizonHours) {
        if (horizonHours < 1 || horizonHours > 72) {
            throw BusinessException.badRequest("INVALID_HORIZON", "horizonHours must be between 1 and 72");
        }
        ForecastInput input = transactionTemplate.execute(status -> buildInput(agentId, horizonHours));
        ForecastResult result = aiClient.post("/internal/ai/v1/float/forecast", input, ForecastResult.class)
                .filter(r -> r.projectedEfloat() != null && r.trajectory() != null)
                .map(r -> new ForecastResult(r.projectedEfloat(), r.projectedCash(), r.minProjectedEfloat(), r.minProjectedCash(),
                        r.predictedEfloatNetFlow(), r.predictedCashNetFlow(), r.hoursToEfloatStockout(), r.hoursToCashStockout(),
                        r.confidence(), r.trajectory(), r.drivers() == null ? List.of() : r.drivers(),
                        r.explanation() == null ? "No explanation supplied by the AI service" : r.explanation(),
                        AiServiceClient.SOURCE_AI, r.modelVersion() == null ? "unknown" : r.modelVersion()))
                .orElseGet(() -> HeuristicFloatForecaster.forecast(input));
        return transactionTemplate.execute(status -> store(agentId, input, result));
    }

    /** Latest stored prediction if it is fresh enough, otherwise a new one. */
    public PredictionResponse latestOrPredict(UUID agentId, Duration maxAge) {
        PredictionResponse latest = transactionTemplate.execute(status ->
                repository.findTopByAgent_IdOrderByCreatedAtDesc(agentId).map(PredictionResponse::from).orElse(null));
        if (latest != null && latest.createdAt().isAfter(AppClock.now().minus(maxAge))) {
            return latest;
        }
        return predict(agentId, DEFAULT_HORIZON_HOURS);
    }

    @Transactional(readOnly = true)
    public PredictionResponse latest(UUID agentId) {
        return repository.findTopByAgent_IdOrderByCreatedAtDesc(agentId).map(PredictionResponse::from)
                .orElseThrow(() -> BusinessException.notFound("Float prediction for agent", agentId));
    }

    /** Ledger history and balances for the AI area-forecast contract. */
    @Transactional(readOnly = true)
    public ForecastInput buildForecastInput(UUID agentId, int horizonHours) {
        if (horizonHours < 1 || horizonHours > 72) {
            throw BusinessException.badRequest("INVALID_HORIZON", "horizonHours must be between 1 and 72");
        }
        return buildInput(agentId, horizonHours);
    }

    private ForecastInput buildInput(UUID agentId, int horizonHours) {
        Agent agent = agentRepository.findById(agentId).orElseThrow(() -> BusinessException.notFound("Agent", agentId));
        BalanceSnapshot balances = accounts.agentBalances(agentId);
        Instant now = AppClock.now();
        Instant since = AppClock.today().minusDays(LOOKBACK_DAYS).atStartOfDay(AppClock.zone()).toInstant();
        List<HourlyFlow> history = new ArrayList<>();
        for (Object[] row : entryRepository.hourlyCustomerFlows(agentId, since, now)) {
            history.add(new HourlyFlow(toInstant(row[0]), Money.of(toDecimal(row[1])), Money.of(toDecimal(row[2]))));
        }
        return new ForecastInput(agentId, agent.getAgentCode(), balances.efloat(), balances.cash(),
                Money.of(agent.getMinEfloatThreshold()), Money.of(agent.getMinCashThreshold()), horizonHours, now,
                AppClock.zone().getId(), LOOKBACK_DAYS, history);
    }

    private PredictionResponse store(UUID agentId, ForecastInput in, ForecastResult r) {
        Agent agent = agentRepository.findById(agentId).orElseThrow(() -> BusinessException.notFound("Agent", agentId));
        boolean efloatShort = r.minProjectedEfloat().compareTo(in.minEfloatThreshold()) < 0;
        boolean cashShort = r.minProjectedCash().compareTo(in.minCashThreshold()) < 0;
        LiquidityRisk risk = efloatShort && cashShort ? LiquidityRisk.BOTH_SHORTAGE
                : efloatShort ? LiquidityRisk.EFLOAT_SHORTAGE : cashShort ? LiquidityRisk.CASH_SHORTAGE : LiquidityRisk.NONE;

        FloatPrediction p = new FloatPrediction();
        p.setAgent(agent);
        p.setHorizonHours(in.horizonHours());
        p.setCurrentEfloat(in.currentEfloat());
        p.setCurrentCash(in.currentCash());
        p.setProjectedEfloat(r.projectedEfloat());
        p.setProjectedCash(r.projectedCash());
        p.setMinProjectedEfloat(r.minProjectedEfloat());
        p.setMinProjectedCash(r.minProjectedCash());
        p.setPredictedEfloatNetFlow(r.predictedEfloatNetFlow());
        p.setPredictedCashNetFlow(r.predictedCashNetFlow());
        p.setHoursToEfloatStockout(r.hoursToEfloatStockout());
        p.setHoursToCashStockout(r.hoursToCashStockout());
        p.setRiskType(risk);
        p.setRecommendedEfloatTopup(efloatShort ? Money.roundUpTo(in.minEfloatThreshold().subtract(r.minProjectedEfloat()), TOPUP_STEP) : Money.ZERO);
        p.setRecommendedCashTopup(cashShort ? Money.roundUpTo(in.minCashThreshold().subtract(r.minProjectedCash()), TOPUP_STEP) : Money.ZERO);
        p.setConfidence(BigDecimal.valueOf(Math.max(0, Math.min(1, r.confidence()))).setScale(4, RoundingMode.HALF_UP));
        p.setTrajectory(r.trajectory());
        p.setDrivers(r.drivers());
        p.setExplanation(r.explanation() + recommendationText(p));
        p.setModelSource(r.modelSource());
        p.setModelVersion(r.modelVersion());
        repository.save(p);
        return PredictionResponse.from(p);
    }

    private String recommendationText(FloatPrediction p) {
        if (p.getRiskType() == LiquidityRisk.NONE) {
            return " No top-up needed within the horizon.";
        }
        StringBuilder sb = new StringBuilder(" Recommended:");
        if (p.getRecommendedEfloatTopup().signum() > 0) {
            sb.append(" e-float top-up of ").append(p.getRecommendedEfloatTopup());
        }
        if (p.getRecommendedCashTopup().signum() > 0) {
            sb.append(p.getRecommendedEfloatTopup().signum() > 0 ? " and" : "").append(" cash top-up of ").append(p.getRecommendedCashTopup());
        }
        return sb.append(" to stay above minimum operating levels.").toString();
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof OffsetDateTime o) {
            return o.toInstant();
        }
        if (value instanceof ZonedDateTime z) {
            return z.toInstant();
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        throw new IllegalStateException("Unexpected timestamp type " + (value == null ? "null" : value.getClass()));
    }

    private static BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal d) {
            return d;
        }
        return value instanceof Number n ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
    }

    public record PredictionResponse(UUID id, UUID agentId, String agentCode, int horizonHours, BigDecimal currentEfloat,
                                     BigDecimal currentCash, BigDecimal projectedEfloat, BigDecimal projectedCash,
                                     BigDecimal minProjectedEfloat, BigDecimal minProjectedCash, BigDecimal predictedEfloatNetFlow,
                                     BigDecimal predictedCashNetFlow, BigDecimal hoursToEfloatStockout,
                                     BigDecimal hoursToCashStockout, LiquidityRisk riskType, BigDecimal recommendedEfloatTopup,
                                     BigDecimal recommendedCashTopup, BigDecimal confidence, List<TrajectoryPoint> trajectory,
                                     List<FeatureContribution> drivers, String explanation, String modelSource,
                                     String modelVersion, Instant createdAt) {
        public static PredictionResponse from(FloatPrediction p) {
            return new PredictionResponse(p.getId(), p.getAgent().getId(), p.getAgent().getAgentCode(), p.getHorizonHours(),
                    Money.of(p.getCurrentEfloat()), Money.of(p.getCurrentCash()), Money.of(p.getProjectedEfloat()),
                    Money.of(p.getProjectedCash()), Money.of(p.getMinProjectedEfloat()), Money.of(p.getMinProjectedCash()),
                    Money.of(p.getPredictedEfloatNetFlow()), Money.of(p.getPredictedCashNetFlow()), p.getHoursToEfloatStockout(),
                    p.getHoursToCashStockout(), p.getRiskType(), Money.of(p.getRecommendedEfloatTopup()),
                    Money.of(p.getRecommendedCashTopup()), p.getConfidence(), p.getTrajectory(), p.getDrivers(),
                    p.getExplanation(), p.getModelSource(), p.getModelVersion(), p.getCreatedAt());
        }
    }
}
