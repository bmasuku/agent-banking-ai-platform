package com.agentbanking.platform.agent;

import com.agentbanking.platform.agent.AgentDtos.AgentProfile;
import com.agentbanking.platform.agent.AgentDtos.CreateAgentRequest;
import com.agentbanking.platform.area.Area;
import com.agentbanking.platform.area.AreaRepository;
import com.agentbanking.platform.audit.AuditService;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.config.AppProperties;
import com.agentbanking.platform.ledger.LedgerAccountService;
import com.agentbanking.platform.user.Role;
import com.agentbanking.platform.user.User;
import com.agentbanking.platform.user.UserRepository;
import com.agentbanking.platform.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Creates an agent: login user, agent profile, agent account and its five ledger accounts, all in
 * one transaction. New agents start with zero balances; float is provided by area replenishment.
 */
@Service
@RequiredArgsConstructor
public class AgentOnboardingService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final AgentRepository agentRepository;
    private final AgentAccountRepository accountRepository;
    private final AreaRepository areaRepository;
    private final LedgerAccountService ledgerAccountService;
    private final AgentService agentService;
    private final AuditService auditService;
    private final AppProperties properties;

    @Transactional
    public AgentProfile onboard(CreateAgentRequest r) {
        return agentService.profile(create(r, null, null).getId());
    }

    /** Shared with the demo seeder, which supplies a fixed agent code and onboarding date. */
    @Transactional
    public Agent create(CreateAgentRequest r, String agentCode, Instant onboardedAt) {
        Area area = areaRepository.findById(r.areaId()).orElseThrow(() -> BusinessException.notFound("Area", r.areaId()));
        User user = userService.buildUser(r.username(), r.email(), r.fullName(), r.phoneNumber(), Role.AGENT, r.password(), area.getId());
        userRepository.save(user);

        Agent agent = new Agent();
        agent.setUser(user);
        agent.setAgentCode(agentCode != null ? agentCode : nextAgentCode());
        agent.setBusinessName(r.businessName());
        agent.setArea(area);
        agent.setLatitude(r.latitude());
        agent.setLongitude(r.longitude());
        agent.setAddress(r.address());
        agent.setMinEfloatThreshold(r.minEfloatThreshold());
        agent.setMinCashThreshold(r.minCashThreshold());
        agent.setCreditLimit(BigDecimal.ZERO);
        agent.setOnboardedAt(onboardedAt);
        agentRepository.save(agent);

        AgentAccount account = new AgentAccount();
        account.setAgent(agent);
        account.setAccountNumber("AA-" + agent.getAgentCode().replace("AGT-", "") + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        account.setCurrency(properties.getCurrency());
        account.setDailyTransactionLimit(r.dailyTransactionLimit());
        accountRepository.save(account);

        ledgerAccountService.provisionAgentAccounts(agent);
        auditService.record("AGENT_ONBOARDED", "AGENT", agent.getId(), Map.of("agentCode", agent.getAgentCode(),
                "username", user.getUsername(), "areaCode", area.getCode()));
        return agent;
    }

    private String nextAgentCode() {
        int next = agentRepository.findHighestAgentCode()
                .map(code -> Integer.parseInt(code.replaceAll("\\D", "")) + 1)
                .orElse(1);
        return String.format("AGT-%03d", next);
    }
}
