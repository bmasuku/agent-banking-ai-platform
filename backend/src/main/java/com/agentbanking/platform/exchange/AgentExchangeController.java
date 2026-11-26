package com.agentbanking.platform.exchange;

import com.agentbanking.platform.common.IdempotentResponses;
import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.Paging;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.exchange.FloatExchangeService.ChatMessageView;
import com.agentbanking.platform.exchange.FloatExchangeService.ChatRequest;
import com.agentbanking.platform.exchange.FloatExchangeService.ExchangeResponse;
import com.agentbanking.platform.exchange.FloatExchangeService.InterestRequest;
import com.agentbanking.platform.exchange.FloatExchangeService.InterestView;
import com.agentbanking.platform.exchange.FloatExchangeService.MatchRequest;
import com.agentbanking.platform.exchange.FloatExchangeService.OpenListingRequest;
import com.agentbanking.platform.exchange.FloatExchangeService.OpenListingView;
import com.agentbanking.platform.exchange.FloatExchangeService.ProposeRequest;
import com.agentbanking.platform.exchange.FloatExchangeService.RespondRequest;
import com.agentbanking.platform.exchange.FloatExchangeService.VerifyRequest;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agent/exchanges")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AGENT')")
@Tag(name = "Agent - Float Exchanges", description = "Anonymous peer float requests, private match, meetup verify tokens, and chat")
public class AgentExchangeController {

    private final FloatExchangeService exchangeService;
    private final AccessControlService access;

    @PostMapping("/open")
    @Operation(summary = "Publish an anonymous open listing for the amount of e-float or cash you need")
    public ResponseEntity<ExchangeResponse> open(@AuthenticationPrincipal UserPrincipal principal,
                                                 @RequestHeader("Idempotency-Key") String key,
                                                 @Valid @RequestBody OpenListingRequest request) {
        ExchangeResponse response = exchangeService.openListing(access.requireAgentId(principal), request, key);
        return IdempotentResponses.of(response, response.replayed());
    }

    @GetMapping("/board")
    @Operation(summary = "Anonymous open listings from nearby agents in my area (no names)")
    public List<OpenListingView> board(@AuthenticationPrincipal UserPrincipal principal) {
        return exchangeService.board(access.requireAgentId(principal));
    }

    @PostMapping
    @Operation(summary = "Propose a directed exchange to a specific peer (legacy)")
    public ResponseEntity<ExchangeResponse> propose(@AuthenticationPrincipal UserPrincipal principal,
                                                    @RequestHeader("Idempotency-Key") String key,
                                                    @Valid @RequestBody ProposeRequest request) {
        ExchangeResponse response = exchangeService.propose(access.requireAgentId(principal), request, key, principal.getId());
        return IdempotentResponses.of(response, response.replayed());
    }

    @GetMapping
    @Operation(summary = "Exchanges I requested or am matched on")
    public PageResponse<ExchangeResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                               @RequestParam(required = false) ExchangeStatus status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return exchangeService.list(access.requireAgentId(principal), null, status, Paging.of(page, size, "requestedAt"));
    }

    @GetMapping("/{id}")
    public ExchangeResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return exchangeService.getForViewer(id, access.requireAgentId(principal));
    }

    @PostMapping("/{id}/interest")
    @Operation(summary = "Anonymously express interest in an open listing")
    public InterestView interest(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                 @Valid @RequestBody(required = false) InterestRequest request) {
        return exchangeService.expressInterest(id, access.requireAgentId(principal), request == null ? new InterestRequest(null) : request);
    }

    @GetMapping("/{id}/interests")
    @Operation(summary = "List anonymous interests on my open listing")
    public List<InterestView> interests(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return exchangeService.listInterests(id, access.requireAgentId(principal));
    }

    @PostMapping("/{id}/match")
    @Operation(summary = "Accept one interest — reveals identities to both parties and issues meetup tokens")
    public ExchangeResponse match(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                  @Valid @RequestBody MatchRequest request) {
        return exchangeService.matchInterest(id, access.requireAgentId(principal), request);
    }

    @PostMapping("/{id}/verify")
    @Operation(summary = "Enter the partner's meetup verification token to prove authenticity")
    public ExchangeResponse verify(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                   @Valid @RequestBody VerifyRequest request) {
        return exchangeService.verifyMeetup(id, access.requireAgentId(principal), request);
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "After both tokens are verified, post the ledger float exchange")
    public ExchangeResponse complete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return exchangeService.completeVerified(id, access.requireAgentId(principal), principal.getId());
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "Chat messages after a match (visible only to matched parties)")
    public List<ChatMessageView> messages(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return exchangeService.listMessages(id, access.requireAgentId(principal));
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Send a chat message to the matched partner")
    public ChatMessageView postMessage(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                       @Valid @RequestBody ChatRequest request) {
        return exchangeService.postMessage(id, access.requireAgentId(principal), request);
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Accept an incoming directed (legacy PENDING) exchange")
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
}
