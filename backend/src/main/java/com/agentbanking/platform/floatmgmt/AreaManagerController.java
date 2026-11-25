package com.agentbanking.platform.floatmgmt;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.agent.AgentDtos.AgentBalanceView;
import com.agentbanking.platform.agent.AgentDtos.AgentLocation;
import com.agentbanking.platform.agent.AgentDtos.AgentProfile;
import com.agentbanking.platform.agent.AgentDtos.AgentSummary;
import com.agentbanking.platform.agent.AgentService;
import com.agentbanking.platform.area.AreaDtos.AreaResponse;
import com.agentbanking.platform.area.AreaService;
import com.agentbanking.platform.common.IdempotentResponses;
import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.Paging;
import com.agentbanking.platform.exchange.ExchangeStatus;
import com.agentbanking.platform.exchange.FloatExchangeService;
import com.agentbanking.platform.exchange.FloatExchangeService.ExchangeResponse;
import com.agentbanking.platform.floatmgmt.FloatManagementService.ReplenishRequest;
import com.agentbanking.platform.floatmgmt.FloatManagementService.ReplenishmentResponse;
import com.agentbanking.platform.fraud.ReviewStatus;
import com.agentbanking.platform.ai.AiAreaForecastService;
import com.agentbanking.platform.ai.AiFloatOptimisationService;
import com.agentbanking.platform.prediction.FloatPredictionService;
import com.agentbanking.platform.prediction.FloatPredictionService.PredictionResponse;
import com.agentbanking.platform.reporting.ReportDtos.AreaReport;
import com.agentbanking.platform.reporting.ReportingService;
import com.agentbanking.platform.risk.RiskDtos.FlaggedTransactionResponse;
import com.agentbanking.platform.risk.RiskService;
import com.agentbanking.platform.security.AccessControlService;
import com.agentbanking.platform.security.UserPrincipal;
import com.agentbanking.platform.transaction.AgentTransactionGuard;
import com.agentbanking.platform.transaction.TransactionDtos.TransactionSummary;
import com.agentbanking.platform.transaction.TransactionQueryService;
import com.agentbanking.platform.transaction.TransactionStatus;
import com.agentbanking.platform.transaction.TransactionType;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/area-manager")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AREA_MANAGER')")
@Tag(name = "Area Manager", description = "Area-scoped agent monitoring, float predictions, replenishment and reports")
public class AreaManagerController {

    private final AccessControlService access;
    private final AreaService areaService;
    private final AgentService agentService;
    private final FloatManagementService floatService;
    private final FloatPredictionService predictionService;
    private final FloatExchangeService exchangeService;
    private final TransactionQueryService transactionQueryService;
    private final RiskService riskService;
    private final ReportingService reportingService;
    private final AiFloatOptimisationService optimisationService;
    private final AiAreaForecastService areaForecastService;

    @GetMapping("/area")
    @Operation(summary = "My area with pool balances")
    public AreaResponse area(@AuthenticationPrincipal UserPrincipal principal) {
        return areaService.get(access.requireAreaId(principal));
    }

    @GetMapping("/agents")
    @Operation(summary = "Agents assigned to my area with current ledger balances")
    public List<AgentSummary> agents(@AuthenticationPrincipal UserPrincipal principal) {
        return agentService.summaries(access.requireAreaId(principal));
    }

    @GetMapping("/agents/{agentId}")
    public AgentProfile agent(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID agentId) {
        requireMyAgent(principal, agentId);
        return agentService.profile(agentId);
    }

    @GetMapping("/agents/{agentId}/balances")
    public AgentBalanceView balances(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID agentId) {
        requireMyAgent(principal, agentId);
        return agentService.balances(agentId);
    }

    @GetMapping("/agents/{agentId}/transactions")
    public PageResponse<TransactionSummary> agentTransactions(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID agentId,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        requireMyAgent(principal, agentId);
        return transactionQueryService.search(new TransactionQueryService.Filter(agentId, null, null, null, null, null, null, null),
                Paging.newestFirst(page, size));
    }

    @GetMapping("/locations")
    @Operation(summary = "Agent locations with liquidity status and latest predicted risk, for the area map")
    public List<AgentLocation> locations(@AuthenticationPrincipal UserPrincipal principal) {
        return agentService.locations(access.requireAreaId(principal));
    }

    @GetMapping("/predictions")
    @Operation(summary = "Float demand predictions for all agents in my area (refreshed if older than 30 minutes)")
    public List<PredictionResponse> predictions(@AuthenticationPrincipal UserPrincipal principal) {
        return agentService.summaries(access.requireAreaId(principal)).stream()
                .map(a -> predictionService.latestOrPredict(a.id(), Duration.ofMinutes(30)))
                .toList();
    }

    @GetMapping("/optimisation")
    @Operation(summary = "AI float optimisation plan for my area (peer swaps and pool top-ups)")
    public Map<String, Object> optimisation(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestParam(defaultValue = "24") int horizonHours) {
        return optimisationService.optimiseArea(access.requireAreaId(principal), horizonHours);
    }

    @GetMapping("/forecast")
    @Operation(summary = "AI area float forecast (ledger histories per agent, or aggregated predictions for very large areas)")
    public Map<String, Object> forecast(@AuthenticationPrincipal UserPrincipal principal,
                                        @RequestParam(defaultValue = "24") int horizonHours) {
        return areaForecastService.forecastArea(access.requireAreaId(principal), horizonHours);
    }

    @PostMapping("/agents/{agentId}/predictions")
    @Operation(summary = "Run a fresh float prediction for one agent")
    public PredictionResponse predict(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID agentId,
                                      @RequestParam(defaultValue = "24") int horizonHours) {
        requireMyAgent(principal, agentId);
        return predictionService.predict(agentId, horizonHours);
    }

    @PostMapping("/agents/{agentId}/replenishments")
    @Operation(summary = "Replenish an agent's e-float or cash from the area pool (pool decreases by the same amount)")
    public ResponseEntity<ReplenishmentResponse> replenish(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID agentId,
                                                           @RequestHeader("Idempotency-Key") String key,
                                                           @Valid @RequestBody ReplenishRequest request) {
        AgentTransactionGuard.validateKey(key);
        requireMyAgent(principal, agentId);
        ReplenishmentResponse response = floatService.replenish(agentId, request, key, principal.getId());
        return IdempotentResponses.of(response, response.replayed());
    }

    @GetMapping("/replenishments")
    public PageResponse<ReplenishmentResponse> replenishments(@AuthenticationPrincipal UserPrincipal principal,
                                                              @RequestParam(required = false) UUID agentId,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return floatService.list(access.requireAreaId(principal), agentId, null, null, Paging.newestFirst(page, size));
    }

    @GetMapping("/exchanges")
    @Operation(summary = "Monitor agent-to-agent float exchanges in my area")
    public PageResponse<ExchangeResponse> exchanges(@AuthenticationPrincipal UserPrincipal principal,
                                                    @RequestParam(required = false) ExchangeStatus status,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return exchangeService.list(null, access.requireAreaId(principal), status, Paging.of(page, size, "requestedAt"));
    }

    @GetMapping("/transactions")
    public PageResponse<TransactionSummary> transactions(@AuthenticationPrincipal UserPrincipal principal,
                                                         @RequestParam(required = false) TransactionType type,
                                                         @RequestParam(required = false) TransactionStatus status,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return transactionQueryService.search(new TransactionQueryService.Filter(null, access.requireAreaId(principal), null, type,
                status, from, to, null), Paging.newestFirst(page, size));
    }

    @GetMapping("/alerts")
    @Operation(summary = "Fraud alerts raised on agents in my area")
    public PageResponse<FlaggedTransactionResponse> alerts(@AuthenticationPrincipal UserPrincipal principal,
                                                           @RequestParam(required = false) ReviewStatus reviewStatus,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return riskService.flagged(new RiskService.FlagFilter(reviewStatus, null, null, null, access.requireAreaId(principal), null, null, true),
                Paging.newestFirst(page, size));
    }

    @GetMapping("/reports/area")
    @Operation(summary = "Area report: balances, transactions, replenishments, exchanges and alerts for a period (default 30 days)")
    public AreaReport report(@AuthenticationPrincipal UserPrincipal principal,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reportingService.areaReport(access.requireAreaId(principal), from, to);
    }

    private void requireMyAgent(UserPrincipal principal, UUID agentId) {
        Agent agent = agentService.require(agentId);
        access.assertAreaManagerOf(principal, agent);
    }
}
