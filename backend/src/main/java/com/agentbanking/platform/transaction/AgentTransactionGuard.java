package com.agentbanking.platform.transaction;

import com.agentbanking.platform.agent.AgentAccount;
import com.agentbanking.platform.agent.AgentAccountRepository;
import com.agentbanking.platform.common.Hashing;
import com.agentbanking.platform.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Concurrency and idempotency primitives shared by every money-moving operation.
 * Callers lock the agent account first; idempotency checks made after the lock are race-free because
 * a concurrent request with the same key for the same agent waits for the lock and then sees the
 * committed transaction. The unique constraint on the key is the final safety net.
 */
@Component
@RequiredArgsConstructor
public class AgentTransactionGuard {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._:\\-]{8,120}$");

    private final AgentAccountRepository agentAccountRepository;
    private final FinancialTransactionRepository transactionRepository;

    public static String validateKey(String idempotencyKey) {
        if (idempotencyKey == null || !KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw BusinessException.badRequest("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key header is required: 8-120 characters of letters, digits, '.', '_', ':' or '-'");
        }
        return idempotencyKey;
    }

    public static String requestHash(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object part : parts) {
            sb.append(part == null ? "" : part.toString().trim()).append('|');
        }
        return Hashing.sha256Hex(sb.toString());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AgentAccount lockAgent(UUID agentId) {
        return agentAccountRepository.lockByAgentId(agentId)
                .orElseThrow(() -> BusinessException.notFound("Agent account", agentId));
    }

    /** Locks two agents in a deterministic order to avoid deadlocks; returns them in argument order. */
    @Transactional(propagation = Propagation.MANDATORY)
    public AgentAccount[] lockAgents(UUID first, UUID second) {
        if (first.compareTo(second) <= 0) {
            AgentAccount a = lockAgent(first);
            AgentAccount b = lockAgent(second);
            return new AgentAccount[]{a, b};
        }
        AgentAccount b = lockAgent(second);
        AgentAccount a = lockAgent(first);
        return new AgentAccount[]{a, b};
    }

    /**
     * Returns the previously completed transaction for this key when the request is a genuine retry.
     * A key reused with a different payload or by a different user is rejected.
     */
    @Transactional(readOnly = true)
    public Optional<FinancialTransaction> findReplay(String idempotencyKey, String requestHash, UUID initiatorId) {
        return transactionRepository.findByIdempotencyKey(idempotencyKey).map(existing -> {
            if (!Objects.equals(existing.getRequestHash(), requestHash) || !Objects.equals(existing.getInitiatedBy(), initiatorId)) {
                throw BusinessException.conflict("IDEMPOTENCY_KEY_REUSED",
                        "Idempotency-Key was already used for a different request");
            }
            return existing;
        });
    }
}
