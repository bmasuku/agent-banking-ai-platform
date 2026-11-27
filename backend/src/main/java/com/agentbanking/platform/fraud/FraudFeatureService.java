package com.agentbanking.platform.fraud;

import com.agentbanking.platform.admin.SettingKey;
import com.agentbanking.platform.admin.SystemSettingService;
import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.agent.AgentRepository;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.GeoUtils;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.customer.Customer;
import com.agentbanking.platform.customer.CustomerRepository;
import com.agentbanking.platform.customer.CustomerService;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Extracts behavioural fraud features for a prospective transaction from committed history. */
@Service
@RequiredArgsConstructor
public class FraudFeatureService {

    static final Set<TransactionType> CUSTOMER_TYPES =
            EnumSet.of(TransactionType.CASH_DEPOSIT, TransactionType.CASH_WITHDRAWAL, TransactionType.AGENT_TRANSFER_TO_CUSTOMER);
    private static final BigDecimal NEAR_THRESHOLD_FACTOR = new BigDecimal("0.95");

    private final AgentRepository agentRepository;
    private final CustomerRepository customerRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final FraudCheckRepository fraudCheckRepository;
    private final SystemSettingService settings;

    @Transactional(readOnly = true)
    public FraudFeatures build(UUID agentId, TransactionType type, BigDecimal amount, String customerPhone) {
        return build(agentId, type, amount, customerPhone, null, null);
    }

    @Transactional(readOnly = true)
    public FraudFeatures build(UUID agentId, TransactionType type, BigDecimal amount, String customerPhone,
                               BigDecimal transactionLatitude, BigDecimal transactionLongitude) {
        Agent agent = agentRepository.findById(agentId).orElseThrow(() -> BusinessException.notFound("Agent", agentId));
        Instant now = AppClock.now();
        Instant oneHourAgo = now.minus(Duration.ofHours(1));
        Instant dayAgo = now.minus(Duration.ofDays(1));
        Instant ninetyDaysAgo = now.minus(Duration.ofDays(90));

        long count1h = transactionRepository.countAgentTransactionsSince(agentId, CUSTOMER_TYPES, oneHourAgo);
        long count24h = transactionRepository.countAgentTransactionsSince(agentId, CUSTOMER_TYPES, dayAgo);

        List<Object[]> statsRows = transactionRepository.agentAmountStats(agentId, now.minus(Duration.ofDays(30)), now);
        Object[] stats = statsRows.isEmpty() ? new Object[]{0, 0, 0} : statsRows.get(0);
        long history = toLong(stats[0]);
        BigDecimal avg = toDecimal(stats[1]).setScale(2, RoundingMode.HALF_UP);
        BigDecimal std = toDecimal(stats[2]).setScale(2, RoundingMode.HALF_UP);
        double zScore = std.signum() > 0 ? amount.subtract(avg).divide(std, 4, RoundingMode.HALF_UP).doubleValue() : 0.0;
        double ratio = avg.signum() > 0 ? amount.divide(avg, 4, RoundingMode.HALF_UP).doubleValue() : 1.0;

        boolean near = false;
        long nearCount = 0;
        for (BigDecimal threshold : settings.getDecimalList(SettingKey.FRAUD_REPORTING_THRESHOLDS)) {
            BigDecimal lower = threshold.multiply(NEAR_THRESHOLD_FACTOR);
            if (amount.compareTo(lower) >= 0 && amount.compareTo(threshold) < 0) {
                near = true;
            }
            nearCount += transactionRepository.countAgentAmountBandSince(agentId, CUSTOMER_TYPES, oneHourAgo, lower, threshold);
        }
        if (near) {
            nearCount++;
        }

        Optional<Customer> customer = customerPhone == null ? Optional.empty()
                : customerRepository.findByPhoneNumber(CustomerService.normalizePhone(customerPhone));
        long customerCount = customer.map(c -> transactionRepository.countCustomerTransactionsSince(c.getId(), CUSTOMER_TYPES, dayAgo)).orElse(0L);
        long customerAgents = customer.map(c -> transactionRepository.countCustomerDistinctAgentsSince(c.getId(), CUSTOMER_TYPES, dayAgo)).orElse(0L);

        int hour = AppClock.nowInZone().getHour();
        long openFlags = fraudCheckRepository.countByAgent_IdAndReviewStatusIn(agentId,
                EnumSet.of(ReviewStatus.PENDING_REVIEW, ReviewStatus.UNDER_INVESTIGATION));
        long priorFlagged90d = fraudCheckRepository.countByAgent_IdAndFlaggedTrueAndCreatedAtAfter(agentId, ninetyDaysAgo);
        long confirmedFraud = fraudCheckRepository.countByAgent_IdAndReviewStatus(agentId, ReviewStatus.CONFIRMED_FRAUD);

        BigDecimal txLat = transactionLatitude;
        BigDecimal txLon = transactionLongitude;
        BigDecimal distance = null;
        if (type.requiresInPersonCustomerPresence()) {
            // Cash-in / cash-out: customer presents ID at the agent — always co-located.
            txLat = agent.getLatitude();
            txLon = agent.getLongitude();
            distance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else if (transactionLatitude != null && transactionLongitude != null
                && agent.getLatitude() != null && agent.getLongitude() != null) {
            distance = GeoUtils.distanceKm(agent.getLatitude(), agent.getLongitude(), transactionLatitude, transactionLongitude);
        }

        return new FraudFeatures(agentId, agent.getAgentCode(), type, amount, hour, hour < 6 || hour >= 22,
                count1h, count24h, history, avg, std, zScore, ratio, near, nearCount,
                customerCount, customerAgents, customer.isEmpty(), agent.getRiskLevel().name(), agent.getCreditScore(),
                openFlags, priorFlagged90d, confirmedFraud,
                agent.getLatitude(), agent.getLongitude(), txLat, txLon, distance);
    }

    private static long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
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
}
