package com.agentbanking.platform.transaction;

import com.agentbanking.platform.common.IdempotentResponses;
import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.Paging;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.security.AccessControlService;
import com.agentbanking.platform.security.UserPrincipal;
import com.agentbanking.platform.transaction.TransactionDtos.CustomerTransactionRequest;
import com.agentbanking.platform.transaction.TransactionDtos.QuoteRequest;
import com.agentbanking.platform.transaction.TransactionDtos.QuoteResponse;
import com.agentbanking.platform.transaction.TransactionDtos.TransactionResponse;
import com.agentbanking.platform.transaction.TransactionDtos.TransactionResult;
import com.agentbanking.platform.transaction.TransactionDtos.TransactionSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agent/transactions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AGENT')")
@Tag(name = "Agent - Transactions", description = """
        Simulated customer transactions. Every completed transaction posts a balanced journal to the immutable ledger.
        POST endpoints require an Idempotency-Key header: 201 = created, 200 + Idempotency-Replayed = retry of an
        earlier request, 422 = blocked by fraud controls (no balances changed).""")
public class AgentTransactionController {

    private final TransactionFacade facade;
    private final TransactionQueryService queryService;
    private final AccessControlService access;

    @PostMapping("/quote")
    @Operation(summary = "Preview fee, commission and balance impact (no money moves)")
    public QuoteResponse quote(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody QuoteRequest request) {
        return facade.quote(access.requireAgentId(principal), request.type(), request.amount());
    }

    @PostMapping("/deposit")
    @Operation(summary = "Customer gives cash; agent sends e-money to the customer wallet (e-float down, cash up)")
    public ResponseEntity<TransactionResponse> deposit(@AuthenticationPrincipal UserPrincipal principal,
                                                       @Parameter(required = true) @RequestHeader("Idempotency-Key") String key,
                                                       @Valid @RequestBody CustomerTransactionRequest request) {
        return respond(facade.execute(access.requireAgentId(principal), principal.getId(), TransactionType.CASH_DEPOSIT, request, key));
    }

    @PostMapping("/withdrawal")
    @Operation(summary = "Customer sends e-money and receives cash (e-float up, cash down)")
    public ResponseEntity<TransactionResponse> withdrawal(@AuthenticationPrincipal UserPrincipal principal,
                                                          @Parameter(required = true) @RequestHeader("Idempotency-Key") String key,
                                                          @Valid @RequestBody CustomerTransactionRequest request) {
        return respond(facade.execute(access.requireAgentId(principal), principal.getId(), TransactionType.CASH_WITHDRAWAL, request, key));
    }

    @PostMapping("/transfer")
    @Operation(summary = "Agent sends e-float to a customer wallet (e-float down)")
    public ResponseEntity<TransactionResponse> transfer(@AuthenticationPrincipal UserPrincipal principal,
                                                        @Parameter(required = true) @RequestHeader("Idempotency-Key") String key,
                                                        @Valid @RequestBody CustomerTransactionRequest request) {
        return respond(facade.execute(access.requireAgentId(principal), principal.getId(), TransactionType.AGENT_TRANSFER_TO_CUSTOMER,
                request, key));
    }

    @GetMapping
    @Operation(summary = "My transactions (including exchanges, replenishments, advances and commission payouts)")
    public PageResponse<TransactionSummary> list(@AuthenticationPrincipal UserPrincipal principal,
                                                 @RequestParam(required = false) TransactionType type,
                                                 @RequestParam(required = false) TransactionStatus status,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        UUID agentId = access.requireAgentId(principal);
        return queryService.search(new TransactionQueryService.Filter(agentId, null, null, type, status, from, to, null),
                Paging.newestFirst(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Transaction detail with ledger entries and fraud evaluation")
    public TransactionResponse detail(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        UUID agentId = access.requireAgentId(principal);
        FinancialTransaction tx = queryService.require(id);
        boolean mine = (tx.getAgent() != null && tx.getAgent().getId().equals(agentId))
                || (tx.getCounterpartyAgent() != null && tx.getCounterpartyAgent().getId().equals(agentId));
        if (!mine) {
            throw BusinessException.forbidden("Transaction does not belong to you");
        }
        return queryService.detail(id);
    }

    private ResponseEntity<TransactionResponse> respond(TransactionResult result) {
        if (result.blocked() && result.created()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result.response());
        }
        return IdempotentResponses.of(result.response(), !result.created());
    }
}
