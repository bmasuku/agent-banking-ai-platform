package com.agentbanking.platform.advance;

import com.agentbanking.platform.advance.AdvanceDtos.AdvanceDetail;
import com.agentbanking.platform.advance.AdvanceDtos.AdvanceRequest;
import com.agentbanking.platform.advance.AdvanceDtos.AdvanceResponse;
import com.agentbanking.platform.advance.AdvanceDtos.RepaymentRequest;
import com.agentbanking.platform.advance.AdvanceDtos.RepaymentResult;
import com.agentbanking.platform.common.IdempotentResponses;
import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.Paging;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.security.AccessControlService;
import com.agentbanking.platform.security.UserPrincipal;
import com.agentbanking.platform.transaction.AgentTransactionGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agent/advances")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AGENT')")
@Tag(name = "Agent - Float Advances", description = """
        Request short-term float credit. Each request is credit-scored (AI service or explainable scorecard) and then
        auto-approved, auto-declined or referred to a finance officer according to platform policy.""")
public class AgentAdvanceController {

    private final AdvanceApplicationService applicationService;
    private final FloatAdvanceService advanceService;
    private final AccessControlService access;

    @PostMapping
    @Operation(summary = "Request a float advance")
    public ResponseEntity<AdvanceResponse> request(@AuthenticationPrincipal UserPrincipal principal,
                                                   @RequestHeader("Idempotency-Key") String key,
                                                   @Valid @RequestBody AdvanceRequest request) {
        AdvanceResponse response = applicationService.apply(access.requireAgentId(principal), request, key, principal.getId());
        return IdempotentResponses.of(response, response.replayed());
    }

    @GetMapping
    @Operation(summary = "My float advances")
    public PageResponse<AdvanceResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                              @RequestParam(required = false) AdvanceStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return advanceService.list(access.requireAgentId(principal), null, status, Paging.of(page, size, "requestedAt"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Advance detail with repayment history")
    public AdvanceDetail detail(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        requireOwn(principal, id);
        return advanceService.detail(id);
    }

    @PostMapping("/{id}/repayments")
    @Operation(summary = "Repay an advance from my e-float (allocated to interest/principal per configured order)")
    public ResponseEntity<RepaymentResult> repay(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                                 @RequestHeader("Idempotency-Key") String key,
                                                 @Valid @RequestBody RepaymentRequest request) {
        AgentTransactionGuard.validateKey(key);
        UUID agentId = access.requireAgentId(principal);
        RepaymentResult result = advanceService.repay(id, request.amount(), key, principal.getId(), RepaymentSource.AGENT_EFLOAT, agentId);
        return IdempotentResponses.of(result, result.replayed());
    }

    private void requireOwn(UserPrincipal principal, UUID advanceId) {
        if (!advanceService.agentIdOf(advanceId).equals(access.requireAgentId(principal))) {
            throw BusinessException.forbidden("Advance does not belong to you");
        }
    }
}
