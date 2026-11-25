package com.agentbanking.platform.commission;

import com.agentbanking.platform.advance.AdvanceDtos.AdvanceDetail;
import com.agentbanking.platform.advance.AdvanceDtos.AdvanceResponse;
import com.agentbanking.platform.advance.AdvanceDtos.ApproveRequest;
import com.agentbanking.platform.advance.AdvanceDtos.RejectRequest;
import com.agentbanking.platform.advance.AdvanceDtos.RepaymentRequest;
import com.agentbanking.platform.advance.AdvanceDtos.RepaymentResult;
import com.agentbanking.platform.advance.AdvanceStatus;
import com.agentbanking.platform.advance.FloatAdvanceService;
import com.agentbanking.platform.advance.RepaymentSource;
import com.agentbanking.platform.commission.CommissionBatchService.BatchResult;
import com.agentbanking.platform.commission.CommissionService.CommissionResponse;
import com.agentbanking.platform.commission.CommissionService.DisbursementPreview;
import com.agentbanking.platform.commission.CommissionService.DisbursementResponse;
import com.agentbanking.platform.common.IdempotentResponses;
import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.Paging;
import com.agentbanking.platform.floatmgmt.FloatManagementService;
import com.agentbanking.platform.floatmgmt.FloatManagementService.FundPoolRequest;
import com.agentbanking.platform.floatmgmt.FloatManagementService.PoolFundingResponse;
import com.agentbanking.platform.floatmgmt.FloatManagementService.ReplenishmentResponse;
import com.agentbanking.platform.floatmgmt.FloatManagementService.VaultFundingRequest;
import com.agentbanking.platform.floatmgmt.FloatManagementService.VaultFundingResponse;
import com.agentbanking.platform.security.UserPrincipal;
import com.agentbanking.platform.transaction.AgentTransactionGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/finance")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('FINANCE_OFFICER','ADMIN')")
@Tag(name = "Finance Officer", description = "Commission disbursement with advance recovery, float advance decisions, area pool funding")
public class FinanceController {

    private final CommissionService commissionService;
    private final CommissionBatchService batchService;
    private final FloatAdvanceService advanceService;
    private final FloatManagementService floatService;

    @GetMapping("/commissions")
    public PageResponse<CommissionResponse> commissions(@RequestParam(required = false) UUID agentId,
                                                        @RequestParam(required = false) CommissionStatus status,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return commissionService.listCommissions(agentId, status, from, to, Paging.of(page, size, "accruedAt"));
    }

    @GetMapping("/commissions/agents/{agentId}/preview")
    @Operation(summary = "Preview a disbursement: gross, interest/principal recovery (capped by policy) and net")
    public DisbursementPreview preview(@PathVariable UUID agentId) {
        return commissionService.preview(agentId);
    }

    @PostMapping("/commissions/agents/{agentId}/disburse")
    @Operation(summary = "Disburse an agent's accrued commission, recovering outstanding advance interest and principal first")
    public ResponseEntity<DisbursementResponse> disburse(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID agentId,
                                                         @RequestHeader("Idempotency-Key") String key) {
        AgentTransactionGuard.validateKey(key);
        DisbursementResponse response = commissionService.disburse(agentId, key, principal.getId());
        return IdempotentResponses.of(response, response.replayed());
    }

    @PostMapping("/commissions/disburse-batch")
    @Operation(summary = "Disburse commission for every agent at or above the minimum payout (one transaction per agent)")
    public BatchResult disburseBatch(@AuthenticationPrincipal UserPrincipal principal, @RequestHeader("Idempotency-Key") String key) {
        return batchService.disburseAll(key, principal.getId());
    }

    @GetMapping("/disbursements")
    public PageResponse<DisbursementResponse> disbursements(@RequestParam(required = false) UUID agentId,
                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return commissionService.listDisbursements(agentId, from, to, Paging.newestFirst(page, size));
    }

    @GetMapping("/advances")
    public PageResponse<AdvanceResponse> advances(@RequestParam(required = false) UUID agentId,
                                                  @RequestParam(required = false) UUID areaId,
                                                  @RequestParam(required = false) AdvanceStatus status,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return advanceService.list(agentId, areaId, status, Paging.of(page, size, "requestedAt"));
    }

    @GetMapping("/advances/{id}")
    @Operation(summary = "Advance detail with the credit decision rationale and repayment history")
    public AdvanceDetail advance(@PathVariable UUID id) {
        return advanceService.detail(id);
    }

    @PostMapping("/advances/{id}/approve")
    @Operation(summary = "Approve a referred advance (disburses immediately; amount capped by the agent's credit limit)")
    public AdvanceResponse approve(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                   @Valid @RequestBody ApproveRequest request) {
        return advanceService.approve(id, request, principal.getId());
    }

    @PostMapping("/advances/{id}/reject")
    public AdvanceResponse reject(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                  @Valid @RequestBody RejectRequest request) {
        return advanceService.reject(id, request.note(), principal.getId());
    }

    @PostMapping("/advances/{id}/repayments")
    @Operation(summary = "Record an off-platform repayment received into the settlement bank")
    public ResponseEntity<RepaymentResult> recordRepayment(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                                           @RequestHeader("Idempotency-Key") String key,
                                                           @Valid @RequestBody RepaymentRequest request) {
        AgentTransactionGuard.validateKey(key);
        RepaymentResult result = advanceService.repay(id, request.amount(), key, principal.getId(), RepaymentSource.FINANCE_RECORDED, null);
        return IdempotentResponses.of(result, result.replayed());
    }

    @PostMapping("/advances/penalties/run")
    @Operation(summary = "Run overdue penalty accrual now (normally scheduled daily); idempotent per advance per day")
    public Map<String, Integer> runPenalties() {
        return Map.of("advancesCharged", advanceService.accruePenalties());
    }

    @PostMapping("/areas/{areaId}/pool-funding")
    @Operation(summary = "Fund an area's e-float and cash pools from the settlement bank and central vault")
    public ResponseEntity<PoolFundingResponse> fundPool(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID areaId,
                                                        @RequestHeader("Idempotency-Key") String key,
                                                        @Valid @RequestBody FundPoolRequest request) {
        AgentTransactionGuard.validateKey(key);
        PoolFundingResponse response = floatService.fundAreaPool(areaId, request, key, principal.getId());
        return IdempotentResponses.of(response, response.replayed());
    }

    @PostMapping("/treasury/vault-funding")
    @Operation(summary = "Record simulated cash capital placed in the central vault (required before cash pools can be funded)")
    public ResponseEntity<VaultFundingResponse> fundVault(@AuthenticationPrincipal UserPrincipal principal,
                                                          @RequestHeader("Idempotency-Key") String key,
                                                          @Valid @RequestBody VaultFundingRequest request) {
        AgentTransactionGuard.validateKey(key);
        VaultFundingResponse response = floatService.fundCentralVault(request, key, principal.getId());
        return IdempotentResponses.of(response, response.replayed());
    }

    @GetMapping("/replenishments")
    public PageResponse<ReplenishmentResponse> replenishments(@RequestParam(required = false) UUID areaId,
                                                              @RequestParam(required = false) UUID agentId,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return floatService.list(areaId, agentId, from, to, Paging.newestFirst(page, size));
    }
}
