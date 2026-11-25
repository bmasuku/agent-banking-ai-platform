package com.agentbanking.platform.ai;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.agent.AgentRepository;
import com.agentbanking.platform.area.Area;
import com.agentbanking.platform.area.AreaService;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.ledger.BalanceType;
import com.agentbanking.platform.ledger.LedgerAccountService;
import com.agentbanking.platform.prediction.FloatPredictionService;
import com.agentbanking.platform.prediction.ForecastModels.ForecastInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds per-agent ledger history and calls the AI area forecast endpoint. When an area has more than
 * {@value #MAX_DIRECT_AI_AGENTS} agents, falls back to aggregating stored {@link FloatPredictionService#latestOrPredict}
 * results (documented in the response {@code source} field).
 */
@Service
@RequiredArgsConstructor
public class AiAreaForecastService {

    static final int MAX_DIRECT_AI_AGENTS = 40;
    private static final int DEFAULT_HORIZON = FloatPredictionService.DEFAULT_HORIZON_HOURS;

    private final AreaService areaService;
    private final AgentRepository agentRepository;
    private final LedgerAccountService ledger;
    private final FloatPredictionService predictionService;
    private final AiServiceClient aiClient;

    @Transactional(readOnly = true)
    public Map<String, Object> forecastArea(UUID areaId, int horizonHours) {
        Area area = areaService.require(areaId);
        List<Agent> agents = agentRepository.findByArea_IdOrderByAgentCode(areaId);
        if (agents.isEmpty()) {
            throw BusinessException.badRequest("NO_AGENTS", "The area has no agents to forecast");
        }
        if (agents.size() > MAX_DIRECT_AI_AGENTS) {
            return aggregateFromPredictions(area, agents, horizonHours);
        }
        BigDecimal poolEfloat = ledger.areaBalance(areaId, BalanceType.AREA_EFLOAT_POOL);
        BigDecimal poolCash = ledger.areaBalance(areaId, BalanceType.AREA_CASH_POOL);
        List<ForecastInput> inputs = agents.stream().map(a -> predictionService.buildForecastInput(a.getId(), horizonHours)).toList();
        AreaForecastRequest body = new AreaForecastRequest(area.getId(), area.getCode(), poolEfloat, poolCash, inputs);
        Map<String, Object> response = aiClient.postRequired("/internal/ai/v1/float/forecast/area", body, Map.class);
        response.put("source", "AI_SERVICE");
        return response;
    }

    private Map<String, Object> aggregateFromPredictions(Area area, List<Agent> agents, int horizonHours) {
        var predictions = agents.stream()
                .map(a -> predictionService.latestOrPredict(a.getId(), java.time.Duration.ofMinutes(30)))
                .toList();
        BigDecimal poolEfloat = ledger.areaBalance(area.getId(), BalanceType.AREA_EFLOAT_POOL);
        BigDecimal poolCash = ledger.areaBalance(area.getId(), BalanceType.AREA_CASH_POOL);
        return Map.of(
                "areaId", area.getId(),
                "areaCode", area.getCode(),
                "source", "AGGREGATED_PREDICTIONS",
                "note", "Area has more than " + MAX_DIRECT_AI_AGENTS + " agents; totals are aggregated from stored float predictions "
                        + "instead of a single AI area-forecast call.",
                "poolEfloat", poolEfloat,
                "poolCash", poolCash,
                "horizonHours", horizonHours > 0 ? horizonHours : DEFAULT_HORIZON,
                "agents", predictions);
    }

    public record AreaForecastRequest(UUID areaId, String areaCode, BigDecimal poolEfloat, BigDecimal poolCash,
                                      List<ForecastInput> agents) {
    }
}