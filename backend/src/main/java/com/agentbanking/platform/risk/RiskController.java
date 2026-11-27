package com.agentbanking.platform.risk;

import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.Paging;
import com.agentbanking.platform.common.RiskLevel;
import com.agentbanking.platform.fraud.FraudDecision;
import com.agentbanking.platform.fraud.ReviewStatus;
import com.agentbanking.platform.risk.RiskDtos.AgentRiskResponse;
import com.agentbanking.platform.risk.RiskDtos.AgentStatusRequest;
import com.agentbanking.platform.risk.RiskDtos.AssignRequest;
import com.agentbanking.platform.risk.RiskDtos.CreateInvestigationRequest;
import com.agentbanking.platform.risk.RiskDtos.FlaggedTransactionResponse;
import com.agentbanking.platform.risk.RiskDtos.FraudCheckDetail;
import com.agentbanking.platform.risk.RiskDtos.InvestigationDetail;
import com.agentbanking.platform.risk.RiskDtos.InvestigationResponse;
import com.agentbanking.platform.risk.RiskDtos.NoteRequest;
import com.agentbanking.platform.risk.RiskDtos.NoteResponse;
import com.agentbanking.platform.risk.RiskDtos.OutcomeRequest;
import com.agentbanking.platform.risk.RiskDtos.ReviewRequest;
import com.agentbanking.platform.risk.RiskDtos.RiskDashboard;
import com.agentbanking.platform.risk.RiskDtos.StatusRequest;
import com.agentbanking.platform.agent.AgentService;
import com.agentbanking.platform.ai.AiAnomalyService;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.security.AccessControlService;
import com.agentbanking.platform.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('RISK_OFFICER','ADMIN')")
@Tag(name = "Risk Officer", description = "Fraud alerts with explainable scores, investigations and agent risk controls")
public class RiskController {

    private final RiskService riskService;
    private final AiAnomalyService aiAnomalyService;
    private final AccessControlService access;
    private final AgentService agentService;

    @GetMapping("/dashboard")
    public RiskDashboard dashboard() {
        return riskService.dashboard();
    }

    @GetMapping("/flagged-transactions")
    @Operation(summary = "Fraud evaluations (flagged only by default) with score, decision and reason codes")
    public PageResponse<FlaggedTransactionResponse> flagged(@RequestParam(required = false) ReviewStatus reviewStatus,
                                                            @RequestParam(required = false) RiskLevel riskLevel,
                                                            @RequestParam(required = false) FraudDecision decision,
                                                            @RequestParam(required = false) UUID agentId,
                                                            @RequestParam(required = false) UUID areaId,
                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                            @RequestParam(defaultValue = "true") boolean flaggedOnly,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return riskService.flagged(new RiskService.FlagFilter(reviewStatus, riskLevel, decision, agentId, areaId, from, to, flaggedOnly),
                Paging.newestFirst(page, size));
    }

    @GetMapping("/fraud-checks/{id}")
    @Operation(summary = "Full fraud evaluation: features, per-feature contributions and review history")
    public FraudCheckDetail fraudCheck(@PathVariable UUID id) {
        return riskService.fraudCheck(id);
    }

    @PostMapping("/fraud-checks/{id}/review")
    @Operation(summary = "Review an alert: CLEARED, CONFIRMED_FRAUD or UNDER_INVESTIGATION (opens a case)")
    public FraudCheckDetail review(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                   @Valid @RequestBody ReviewRequest request) {
        return riskService.review(id, request.status(), request.note(), principal.getId());
    }

    @GetMapping("/investigations")
    public PageResponse<InvestigationResponse> investigations(@RequestParam(required = false) InvestigationStatus status,
                                                              @RequestParam(required = false) RiskLevel severity,
                                                              @RequestParam(required = false) UUID agentId,
                                                              @RequestParam(required = false) UUID assigneeId,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return riskService.list(status, severity, agentId, assigneeId, Paging.of(page, size, "openedAt"));
    }

    @PostMapping("/investigations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvestigationResponse create(@AuthenticationPrincipal UserPrincipal principal,
                                        @Valid @RequestBody CreateInvestigationRequest request) {
        return riskService.create(request, principal.getId());
    }

    @GetMapping("/investigations/{id}")
    public InvestigationDetail investigation(@PathVariable UUID id) {
        return riskService.detail(id);
    }

    @PostMapping("/investigations/{id}/assign")
    public InvestigationResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest request) {
        return riskService.assign(id, request.assigneeId());
    }

    @PostMapping("/investigations/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse addNote(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                @Valid @RequestBody NoteRequest request) {
        return riskService.addNote(id, request.note(), principal.getId());
    }

    @PostMapping("/investigations/{id}/status")
    public InvestigationResponse status(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                        @Valid @RequestBody StatusRequest request) {
        return riskService.updateStatus(id, request.status(), request.note(), principal.getId());
    }

    @PostMapping("/investigations/{id}/outcome")
    @Operation(summary = "Close an investigation with an outcome and remedial actions (freeze, suspend, reinstate, limit, block)")
    public InvestigationResponse outcome(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                         @Valid @RequestBody OutcomeRequest request) {
        return riskService.close(id, request, principal.getId());
    }

    @GetMapping("/agents")
    @Operation(summary = "Agent risk overview sorted by risk level")
    public List<AgentRiskResponse> agents(@RequestParam(required = false) UUID areaId) {
        return riskService.agentRiskOverview(areaId);
    }

    @PostMapping("/agents/{agentId}/status")
    @Operation(summary = "Freeze, suspend, place under review or reactivate an agent")
    public AgentRiskResponse agentStatus(@PathVariable UUID agentId, @Valid @RequestBody AgentStatusRequest request) {
        return riskService.setAgentStatus(agentId, request.status(), request.reason());
    }

    @GetMapping("/agents/{agentId}/anomalies")
    @Operation(summary = "AI anomaly detection for an agent (72h recent window by default), enriched with open fraud alerts")
    public Map<String, Object> agentAnomalies(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID agentId,
                                              @RequestParam(defaultValue = "72") int recentWindowHours) {
        access.assertCanViewAgent(principal, agentService.require(agentId));
        return aiAnomalyService.detectAgentAnomalies(agentId, recentWindowHours);
    }

    @GetMapping("/areas/{areaId}/peer-anomalies")
    @Operation(summary = "Peer-group anomaly comparison for agents in an area (last 7 days)")
    public Map<String, Object> peerAnomalies(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID areaId) {
        if (!access.canAccessArea(principal, areaId)) {
            throw BusinessException.forbidden("You are not permitted to access this area");
        }
        return aiAnomalyService.compareAreaPeers(areaId);
    }

    @GetMapping("/peer-comparison")
    @Operation(summary = "Peer-group anomaly comparison (alias with areaId query parameter)")
    public Map<String, Object> peerComparison(@AuthenticationPrincipal UserPrincipal principal, @RequestParam UUID areaId) {
        return peerAnomalies(principal, areaId);
    }
}
