package com.agentbanking.platform.security;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.agent.AgentRepository;
import com.agentbanking.platform.user.Role;
import com.agentbanking.platform.user.User;
import com.agentbanking.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Builds a fresh principal from the database so role, area and status changes take effect immediately. */
@Component
@RequiredArgsConstructor
public class PrincipalLoader {

    private final UserRepository userRepository;
    private final AgentRepository agentRepository;

    @Transactional(readOnly = true)
    public Optional<UserPrincipal> load(UUID userId, String tokenId, Instant tokenExpiresAt) {
        return userRepository.findById(userId).map(user -> build(user, tokenId, tokenExpiresAt));
    }

    @Transactional(readOnly = true)
    public UserPrincipal build(User user, String tokenId, Instant tokenExpiresAt) {
        UUID agentId = null;
        UUID agentAreaId = null;
        if (user.getRole() == Role.AGENT) {
            Optional<Agent> agent = agentRepository.findByUser_Id(user.getId());
            if (agent.isPresent()) {
                agentId = agent.get().getId();
                agentAreaId = agent.get().getArea().getId();
            }
        }
        return UserPrincipal.from(user, agentId, agentAreaId, tokenId, tokenExpiresAt);
    }
}
