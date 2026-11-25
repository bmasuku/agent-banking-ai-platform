package com.agentbanking.platform.credit;

import com.agentbanking.platform.agent.AgentService;
import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.Paging;
import com.agentbanking.platform.credit.CreditScoringService.CreditScoreResponse;
import com.agentbanking.platform.security.AccessControlService;
import com.agentbanking.platform.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/credit-scores/agents/{agentId}")
@RequiredArgsConstructor
@Tag(name = "Credit Scores", description = "Explainable agent credit scores (0-1000) with per-factor contributions")
public class CreditScoreController {

    private final CreditScoringService creditScoringService;
    private final AgentService agentService;
    private final AccessControlService access;

    @GetMapping
    @Operation(summary = "Latest credit score with factor explanations")
    public CreditScoreResponse latest(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID agentId) {
        access.assertCanViewAgent(principal, agentService.require(agentId));
        return creditScoringService.latest(agentId);
    }

    @GetMapping("/history")
    public PageResponse<CreditScoreResponse> history(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID agentId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        access.assertCanViewAgent(principal, agentService.require(agentId));
        return creditScoringService.history(agentId, Paging.newestFirst(page, size));
    }

    @PostMapping("/evaluate")
    @PreAuthorize("hasAnyRole('RISK_OFFICER','FINANCE_OFFICER','ADMIN')")
    @Operation(summary = "Re-score an agent now (updates credit limit, risk level and tier)")
    public CreditScoreResponse evaluate(@PathVariable UUID agentId, @RequestParam(required = false) BigDecimal requestedAmount) {
        agentService.require(agentId);
        return creditScoringService.evaluateAndStore(agentId, requestedAmount);
    }
}
