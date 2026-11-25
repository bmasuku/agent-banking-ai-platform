package com.agentbanking.platform.ai;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.agent.AgentService;
import com.agentbanking.platform.area.Area;
import com.agentbanking.platform.area.AreaService;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.transaction.FinancialTransaction;
import com.agentbanking.platform.transaction.FinancialTransactionRepository;
import com.agentbanking.platform.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiAnomalyService {

    private static final int BASELINE_DAYS = 30;
    private static final EnumSet<TransactionType> CUSTOMER_TYPES = EnumSet.of(
            TransactionType.CASH_DEPOSIT, TransactionType.CASH_WITHDRAWAL, TransactionType.AGENT_TRANSFER_TO_CUSTOMER);

    private final AgentService agentService;
    private final AreaService areaService;
    private final FinancialTransactionRepository transactionRepository;
    private final AiServiceClient aiClient;

    @Transactional(readOnly = true)
    public Map<String, Object> detectAgentAnomalies(UUID agentId, int recentWindowHours) {
        if (recentWindowHours < 1 || recentWindowHours > 168) {
            throw BusinessException.badRequest("INVALID_WINDOW", "recentWindowHours must be between 1 and 168");
        }
        Agent agent = agentService.require(agentId);
        Instant asOf = AppClock.now();
        Instant since = asOf.minus(Duration.ofDays(BASELINE_DAYS));
        List<FinancialTransaction> txs = transactionRepository.findAgentTransactionsBetween(agentId, since, asOf, CUSTOMER_TYPES);
        List<AnomalyTransactionRecord> records = txs.stream()
                .filter(t -> t.getAmount().compareTo(BigDecimal.ZERO) > 0)
                .map(t -> new AnomalyTransactionRecord(
                        t.getId().toString(), t.getCreatedAt(), t.getType().name(), Money.of(t.getAmount()), t.getStatus().name()))
                .toList();
        AnomalyDetectRequest body = new AnomalyDetectRequest(agent.getId(), agent.getAgentCode(), asOf, AppClock.zone().getId(),
                recentWindowHours, records);
        return aiClient.postRequired("/internal/ai/v1/anomaly/detect", body, Map.class);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> compareAreaPeers(UUID areaId) {
        Area area = areaService.require(areaId);
        Instant until = AppClock.now();
        Instant since = until.minus(Duration.ofDays(7));
        List<Object[]> rows = transactionRepository.peerAnomalyFeatures(areaId, since, until);
        if (rows.size() < 2) {
            throw BusinessException.badRequest("INSUFFICIENT_PEERS", "Peer comparison needs at least two agents in the area");
        }
        List<PeerAgentPayload> agents = rows.stream().map(this::toPeerAgent).toList();
        PeerComparisonRequest body = new PeerComparisonRequest(area.getId(), area.getCode(), agents);
        return aiClient.postRequired("/internal/ai/v1/anomaly/peer-comparison", body, Map.class);
    }

    private PeerAgentPayload toPeerAgent(Object[] row) {
        UUID agentId = row[0] instanceof UUID u ? u : UUID.fromString(row[0].toString());
        String agentCode = row[1].toString();
        long transactions = toLong(row[2]);
        BigDecimal volume = toDecimal(row[3]);
        long offHours = toLong(row[4]);
        long withdrawals = toLong(row[5]);
        long reversals = toLong(row[6]);
        long distinctCustomers = toLong(row[7]);
        long flagged = toLong(row[8]);
        double offHoursShare = transactions == 0 ? 0.0 : (double) offHours / transactions;
        double withdrawalShare = transactions == 0 ? 0.0 : (double) withdrawals / transactions;
        double avgAmount = transactions == 0 ? 0.0 : volume.doubleValue() / transactions;
        PeerFeaturesPayload features = new PeerFeaturesPayload(transactions, volume.doubleValue(), avgAmount, offHoursShare,
                withdrawalShare, flagged, reversals, distinctCustomers);
        return new PeerAgentPayload(agentId, agentCode, features);
    }

    private static long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return value == null ? 0L : Long.parseLong(value.toString());
    }

    private static BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal d) {
            return d;
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return BigDecimal.ZERO;
    }

    public record AnomalyTransactionRecord(String id, Instant timestamp, String type, BigDecimal amount, String status) {
    }

    public record AnomalyDetectRequest(UUID agentId, String agentCode, Instant asOf, String timezone, int recentWindowHours,
                                       List<AnomalyTransactionRecord> transactions) {
    }

    public record PeerFeaturesPayload(long transactions7d, double volume7d, double avgAmount7d, double offHoursShare7d,
                                      double withdrawalShare7d, long flagged7d, long reversals7d, long distinctCustomers7d) {
        public PeerFeaturesPayload {
            volume7d = round(volume7d);
            avgAmount7d = round(avgAmount7d);
            offHoursShare7d = round(offHoursShare7d);
            withdrawalShare7d = round(withdrawalShare7d);
        }

        private static double round(double v) {
            return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
        }
    }

    public record PeerAgentPayload(UUID agentId, String agentCode, PeerFeaturesPayload features) {
    }

    public record PeerComparisonRequest(UUID areaId, String areaCode, List<PeerAgentPayload> agents) {
    }
}