package com.agentbanking.platform.exchange;

import com.agentbanking.platform.common.IdempotentResponses;
import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.Paging;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.exchange.FloatExchangeService.ExchangeResponse;
import com.agentbanking.platform.exchange.FloatExchangeService.ProposeRequest;
import com.agentbanking.platform.exchange.FloatExchangeService.RespondRequest;
import com.agentbanking.platform.security.AccessControlService;
import com.agentbanking.platform.security.UserPrincipal;
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
@RequestMapping("/api/v1/agent/exchanges")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AGENT')")
@Tag(name = "Agent - Float Exchanges", description = "Agent-to-agent e-float/cash exchange; both balances update atomically on acceptance")
public class AgentExchangeController {

    private final FloatExchangeService exchangeService;
    private final AccessControlService access;

    @PostMapping
    @Operation(summary = "Propose an exchange to a peer agent in my area")
    public ResponseEntity<ExchangeResponse> propose(@AuthenticationPrincipal UserPrincipal principal,
                                                    @RequestHeader("Idempotency-Key") String key,
                                                    @Valid @RequestBody ProposeRequest request) {
        ExchangeResponse response = exchangeService.propose(access.requireAgentId(principal), request, key, principal.getId());
        return IdempotentResponses.of(response, response.replayed());
    }

    @GetMapping
    @Operation(summary = "Exchanges I requested or received")
    public PageResponse<ExchangeResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                               @RequestParam(required = false) ExchangeStatus status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return exchangeService.list(access.requireAgentId(principal), null, status, Paging.of(page, size, "requestedAt"));
    }

    @GetMapping("/{id}")
    public ExchangeResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        requireParticipant(principal, id);
        return exchangeService.get(id);
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Accept an incoming exchange (posts the ledger journal for both agents)")
    public ExchangeResponse accept(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                   @Valid @RequestBody(required = false) RespondRequest request) {
        return exchangeService.accept(id, access.requireAgentId(principal), request == null ? null : request.note(), principal.getId());
    }

    @PostMapping("/{id}/reject")
    public ExchangeResponse reject(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                   @Valid @RequestBody(required = false) RespondRequest request) {
        return exchangeService.reject(id, access.requireAgentId(principal), request == null ? null : request.note());
    }

    @PostMapping("/{id}/cancel")
    public ExchangeResponse cancel(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return exchangeService.cancel(id, access.requireAgentId(principal));
    }

    private void requireParticipant(UserPrincipal principal, UUID exchangeId) {
        UUID agentId = access.requireAgentId(principal);
        UUID[] participants = exchangeService.participants(exchangeId);
        if (!participants[0].equals(agentId) && !participants[1].equals(agentId)) {
            throw BusinessException.forbidden("You are not a party to this exchange");
        }
    }
}
