package com.agentbanking.platform.agent;

import com.agentbanking.platform.agent.AgentDtos.AgentBalanceView;
import com.agentbanking.platform.agent.AgentDtos.AgentLocation;
import com.agentbanking.platform.agent.AgentDtos.AgentProfile;
import com.agentbanking.platform.agent.AgentDtos.AgentSummary;
import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.Paging;
import com.agentbanking.platform.ledger.BalanceType;
import com.agentbanking.platform.ledger.LedgerQueryService.StatementLine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('RISK_OFFICER','FINANCE_OFFICER','ADMIN')")
@Tag(name = "Agents (staff)", description = "Network-wide agent directory for head-office staff")
public class AgentDirectoryController {

    private final AgentService agentService;

    @GetMapping
    @Operation(summary = "All agents (optionally one area) with ledger balances")
    public List<AgentSummary> list(@RequestParam(required = false) UUID areaId) {
        return agentService.summaries(areaId);
    }

    @GetMapping("/locations")
    public List<AgentLocation> locations(@RequestParam(required = false) UUID areaId) {
        return agentService.locations(areaId);
    }

    @GetMapping("/{agentId}")
    public AgentProfile profile(@PathVariable UUID agentId) {
        return agentService.profile(agentId);
    }

    @GetMapping("/{agentId}/balances")
    public AgentBalanceView balances(@PathVariable UUID agentId) {
        return agentService.balances(agentId);
    }

    @GetMapping("/{agentId}/statement")
    public PageResponse<StatementLine> statement(@PathVariable UUID agentId,
                                                 @RequestParam(defaultValue = "EFLOAT") BalanceType type,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return agentService.statement(agentId, type, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), Paging.MAX_SIZE)));
    }
}
