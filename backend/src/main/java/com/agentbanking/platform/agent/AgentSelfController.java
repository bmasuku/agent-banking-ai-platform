package com.agentbanking.platform.agent;

import com.agentbanking.platform.agent.AgentDtos.AgentBalanceView;
import com.agentbanking.platform.agent.AgentDtos.AgentProfile;
import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.Paging;
import com.agentbanking.platform.customer.CustomerService;
import com.agentbanking.platform.customer.CustomerService.CustomerLookup;
import com.agentbanking.platform.exchange.FloatExchangeService;
import com.agentbanking.platform.exchange.FloatExchangeService.PeerResponse;
import com.agentbanking.platform.ledger.BalanceType;
import com.agentbanking.platform.ledger.LedgerQueryService.StatementLine;
import com.agentbanking.platform.security.AccessControlService;
import com.agentbanking.platform.security.UserPrincipal;
import com.agentbanking.platform.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AGENT')")
@Tag(name = "Agent - Profile & Balances", description = "Authenticated agent's own profile, ledger balances and statements")
public class AgentSelfController {

    private final AgentService agentService;
    private final AccessControlService access;
    private final CustomerService customerService;
    private final FloatExchangeService exchangeService;

    @GetMapping("/me")
    @Operation(summary = "My agent profile")
    public AgentProfile me(@AuthenticationPrincipal UserPrincipal principal) {
        return agentService.profile(access.requireAgentId(principal));
    }

    @GetMapping("/balances")
    @Operation(summary = "My balances (e-float, cash, commission payable, advance principal and interest) read from the ledger")
    public AgentBalanceView balances(@AuthenticationPrincipal UserPrincipal principal) {
        return agentService.balances(access.requireAgentId(principal));
    }

    @GetMapping("/statement")
    @Operation(summary = "Ledger statement for one of my balance types")
    public PageResponse<StatementLine> statement(@AuthenticationPrincipal UserPrincipal principal,
                                                 @RequestParam(defaultValue = "EFLOAT") BalanceType type,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return agentService.statement(access.requireAgentId(principal), type,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), Paging.MAX_SIZE)));
    }

    @GetMapping("/customers/lookup")
    @Operation(summary = "Look up a registered customer by phone number before a transaction")
    public CustomerLookup lookup(@AuthenticationPrincipal UserPrincipal principal, @RequestParam String phone) {
        access.requireAgentId(principal);
        return customerService.findByPhone(phone).map(CustomerLookup::from)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "No registered customer with phone " + phone));
    }

    @GetMapping("/peers")
    @Operation(summary = "Nearby agents in my area available for float exchange, sorted by distance")
    public List<PeerResponse> peers(@AuthenticationPrincipal UserPrincipal principal) {
        return exchangeService.peers(access.requireAgentId(principal));
    }
}
