package com.agentbanking.platform.transaction;

import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.Paging;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.security.UserPrincipal;
import com.agentbanking.platform.transaction.TransactionDtos.TransactionResponse;
import com.agentbanking.platform.transaction.TransactionDtos.TransactionSummary;
import com.agentbanking.platform.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('AREA_MANAGER','RISK_OFFICER','FINANCE_OFFICER','ADMIN')")
@Tag(name = "Transactions (staff)", description = "Transaction search with ledger entries; area managers are restricted to their area")
public class TransactionQueryController {

    private final TransactionQueryService queryService;

    @GetMapping
    public PageResponse<TransactionSummary> search(@AuthenticationPrincipal UserPrincipal principal,
                                                   @RequestParam(required = false) UUID agentId,
                                                   @RequestParam(required = false) UUID areaId,
                                                   @RequestParam(required = false) UUID customerId,
                                                   @RequestParam(required = false) TransactionType type,
                                                   @RequestParam(required = false) TransactionStatus status,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                   @RequestParam(required = false) String reference,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        UUID effectiveArea = areaId;
        if (principal.getRole() == Role.AREA_MANAGER) {
            if (areaId != null && !areaId.equals(principal.getAreaId())) {
                throw BusinessException.forbidden("Area managers can only query their own area");
            }
            effectiveArea = principal.getAreaId();
        }
        return queryService.search(new TransactionQueryService.Filter(agentId, effectiveArea, customerId, type, status, from, to, reference),
                Paging.newestFirst(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Transaction detail including its balanced journal lines and fraud evaluation")
    public TransactionResponse detail(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        if (principal.getRole() == Role.AREA_MANAGER) {
            FinancialTransaction tx = queryService.require(id);
            if (tx.getAreaId() == null || !tx.getAreaId().equals(principal.getAreaId())) {
                throw BusinessException.forbidden("Transaction is outside your area");
            }
        }
        return queryService.detail(id);
    }
}
