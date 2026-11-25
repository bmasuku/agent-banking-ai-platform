package com.agentbanking.platform.advance;

import com.agentbanking.platform.advance.AdvanceDtos.AdvanceRequest;
import com.agentbanking.platform.advance.AdvanceDtos.AdvanceResponse;
import com.agentbanking.platform.credit.CreditScoringService;
import com.agentbanking.platform.credit.CreditScoringService.CreditScoreResponse;
import com.agentbanking.platform.transaction.AgentTransactionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates an advance request: credit evaluation (which may call the AI service) happens before
 * the database transaction that applies the decision, so no locks are held during the remote call.
 */
@Service
@RequiredArgsConstructor
public class AdvanceApplicationService {

    private final CreditScoringService creditScoringService;
    private final FloatAdvanceService advanceService;

    public AdvanceResponse apply(UUID agentId, AdvanceRequest request, String idempotencyKey, UUID requestedBy) {
        AgentTransactionGuard.validateKey(idempotencyKey);
        Optional<AdvanceResponse> replay = advanceService.findReplay(idempotencyKey, agentId);
        if (replay.isPresent()) {
            return replay.get();
        }
        CreditScoreResponse credit = creditScoringService.evaluateAndStore(agentId, request.amount());
        return advanceService.submit(agentId, request, idempotencyKey, requestedBy, credit);
    }
}
