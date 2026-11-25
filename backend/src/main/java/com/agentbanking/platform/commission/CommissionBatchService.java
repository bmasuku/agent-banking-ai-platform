package com.agentbanking.platform.commission;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.agent.AgentRepository;
import com.agentbanking.platform.commission.CommissionService.DisbursementResponse;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.transaction.AgentTransactionGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runs commission disbursement for every eligible agent. Each agent is processed in its own transaction
 * (via {@link CommissionService#disburse}) with a derived idempotency key, so a retried batch never pays twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionBatchService {

    private static final int MAX_BATCH_KEY_LENGTH = 100;

    private final CommissionService commissionService;
    private final AgentRepository agentRepository;

    public record BatchFailure(UUID agentId, String agentCode, String code, String message) {
    }

    public record BatchResult(String batchKey, int processed, int replayed, BigDecimal totalGross, BigDecimal totalRecovered,
                              BigDecimal totalNet, List<DisbursementResponse> disbursements, List<BatchFailure> failures) {
    }

    public BatchResult disburseAll(String batchKey, UUID processedBy) {
        AgentTransactionGuard.validateKey(batchKey);
        if (batchKey.length() > MAX_BATCH_KEY_LENGTH) {
            throw BusinessException.badRequest("INVALID_IDEMPOTENCY_KEY", "Batch Idempotency-Key must be at most 100 characters");
        }
        List<DisbursementResponse> done = new ArrayList<>();
        List<BatchFailure> failures = new ArrayList<>();
        for (UUID agentId : commissionService.agentsEligibleForPayout()) {
            String agentCode = agentRepository.findById(agentId).map(Agent::getAgentCode).orElse(agentId.toString());
            try {
                done.add(commissionService.disburse(agentId, batchKey + ":" + agentCode, processedBy));
            } catch (BusinessException e) {
                log.warn("Commission disbursement for {} failed in batch {}: {}", agentCode, batchKey, e.getMessage());
                failures.add(new BatchFailure(agentId, agentCode, e.getErrorCode(), e.getMessage()));
            }
        }
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal recovered = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        int replayed = 0;
        for (DisbursementResponse d : done) {
            gross = gross.add(d.grossCommission());
            recovered = recovered.add(d.interestRecovered()).add(d.principalRecovered());
            net = net.add(d.netCommission());
            if (d.replayed()) {
                replayed++;
            }
        }
        return new BatchResult(batchKey, done.size(), replayed, gross, recovered, net, done, failures);
    }
}
