package com.agentbanking.platform.commission;

import com.agentbanking.platform.commission.CommissionService.CommissionResponse;
import com.agentbanking.platform.commission.CommissionService.CommissionSummary;
import com.agentbanking.platform.commission.CommissionService.DisbursementResponse;
import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.Paging;
import com.agentbanking.platform.security.AccessControlService;
import com.agentbanking.platform.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent/commissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AGENT')")
@Tag(name = "Agent - Commissions", description = "Accrued commission and disbursement history, including advance recovery")
public class AgentCommissionController {

    private final CommissionService commissionService;
    private final AccessControlService access;

    @GetMapping("/summary")
    @Operation(summary = "Accrued commission, lifetime payouts and a preview of the next disbursement")
    public CommissionSummary summary(@AuthenticationPrincipal UserPrincipal principal) {
        return commissionService.summary(access.requireAgentId(principal));
    }

    @GetMapping
    @Operation(summary = "Commission earned per transaction")
    public PageResponse<CommissionResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                                 @RequestParam(required = false) CommissionStatus status,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return commissionService.listCommissions(access.requireAgentId(principal), status, null, null,
                Paging.of(page, size, "accruedAt"));
    }

    @GetMapping("/disbursements")
    @Operation(summary = "My commission disbursements: gross, principal recovered, interest recovered and net")
    public PageResponse<DisbursementResponse> disbursements(@AuthenticationPrincipal UserPrincipal principal,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return commissionService.listDisbursements(access.requireAgentId(principal), null, null, Paging.newestFirst(page, size));
    }
}
