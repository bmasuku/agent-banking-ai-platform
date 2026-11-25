package com.agentbanking.platform.ai;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.agent.AgentRepository;
import com.agentbanking.platform.agent.AgentStatus;
import com.agentbanking.platform.area.Area;
import com.agentbanking.platform.area.AreaService;
import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.ledger.BalanceSnapshot;
import com.agentbanking.platform.ledger.BalanceType;
import com.agentbanking.platform.ledger.LedgerAccountService;
import com.agentbanking.platform.prediction.FloatPrediction;
import com.agentbanking.platform.prediction.FloatPredictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiFloatOptimisationService {

    private final AreaService areaService;
    private final AgentRepository agentRepository;
    private final LedgerAccountService ledger;
    private final FloatPredictionRepository predictionRepository;
    private final AiServiceClient aiClient;

    @Transactional(readOnly = true)
    public Map<String, Object> optimiseArea(UUID areaId, int horizonHours) {
        Area area = areaService.require(areaId);
        BigDecimal poolEfloat = ledger.areaBalance(areaId, BalanceType.AREA_EFLOAT_POOL);
        BigDecimal poolCash = ledger.areaBalance(areaId, BalanceType.AREA_CASH_POOL);
        List<Agent> agents = agentRepository.findByArea_IdOrderByAgentCode(areaId);
        List<AgentPositionPayload> positions = agents.stream().map(a -> toPosition(a, horizonHours)).toList();
        OptimisationRequest body = new OptimisationRequest(area.getId(), area.getCode(), poolEfloat, poolCash, horizonHours,
                positions);
        return aiClient.postRequired("/internal/ai/v1/float/optimise", body, Map.class);
    }

    private AgentPositionPayload toPosition(Agent agent, int horizonHours) {
        BalanceSnapshot balances = ledger.agentBalances(agent.getId());
        var latest = predictionRepository.findTopByAgent_IdOrderByCreatedAtDesc(agent.getId());
        BigDecimal expectedEfloatFlow = latest.map(FloatPrediction::getPredictedEfloatNetFlow).orElse(BigDecimal.ZERO);
        BigDecimal expectedCashFlow = latest.map(FloatPrediction::getPredictedCashNetFlow).orElse(BigDecimal.ZERO);
        Double forecastConfidence = latest.map(p -> p.getConfidence().doubleValue()).orElse(null);
        BigDecimal hoursEfloat = latest.map(FloatPrediction::getHoursToEfloatStockout).orElse(null);
        BigDecimal hoursCash = latest.map(FloatPrediction::getHoursToCashStockout).orElse(null);
        return new AgentPositionPayload(agent.getId(), agent.getAgentCode(), agent.getLatitude(), agent.getLongitude(),
                balances.efloat(), balances.cash(), Money.of(agent.getMinEfloatThreshold()), Money.of(agent.getMinCashThreshold()),
                expectedEfloatFlow, expectedCashFlow, hoursEfloat, hoursCash, forecastConfidence,
                agent.getStatus() == AgentStatus.ACTIVE || agent.getStatus() == AgentStatus.UNDER_REVIEW);
    }

    public record AgentPositionPayload(UUID agentId, String agentCode, BigDecimal latitude, BigDecimal longitude,
                                       BigDecimal efloat, BigDecimal cash, BigDecimal minEfloat, BigDecimal minCash,
                                       BigDecimal expectedEfloatNetFlow, BigDecimal expectedCashNetFlow,
                                       BigDecimal hoursToEfloatStockout, BigDecimal hoursToCashStockout, Double forecastConfidence,
                                       boolean active) {
    }

    public record OptimisationRequest(UUID areaId, String areaCode, BigDecimal poolEfloat, BigDecimal poolCash, int horizonHours,
                                      List<AgentPositionPayload> agents) {
        public OptimisationRequest {
            if (horizonHours < 1) {
                horizonHours = 24;
            }
        }
    }
}