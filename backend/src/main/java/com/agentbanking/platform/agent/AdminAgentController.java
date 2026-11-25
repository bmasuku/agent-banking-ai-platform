package com.agentbanking.platform.agent;

import com.agentbanking.platform.agent.AgentDtos.AgentProfile;
import com.agentbanking.platform.agent.AgentDtos.CreateAgentRequest;
import com.agentbanking.platform.agent.AgentDtos.UpdateAgentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/agents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Agents", description = "Agent onboarding and profile maintenance")
public class AdminAgentController {

    private final AgentOnboardingService onboardingService;
    private final AgentService agentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Onboard an agent: login user, agent account and ledger accounts (zero opening balances)")
    public AgentProfile create(@Valid @RequestBody CreateAgentRequest request) {
        return onboardingService.onboard(request);
    }

    @PutMapping("/{agentId}")
    @Operation(summary = "Update business details, location, liquidity thresholds and daily limit")
    public AgentProfile update(@PathVariable UUID agentId, @Valid @RequestBody UpdateAgentRequest request) {
        return agentService.update(agentId, request);
    }
}
