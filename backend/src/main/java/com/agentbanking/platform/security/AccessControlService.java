package com.agentbanking.platform.security;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.user.Role;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Resource-level authorization: role checks happen on endpoints, this class enforces ownership
 * (agents see only themselves) and area scoping (area managers see only agents in their area).
 */
@Service
public class AccessControlService {

    public void assertCanViewAgent(UserPrincipal principal, Agent agent) {
        if (!canViewAgent(principal, agent.getId(), agent.getArea().getId())) {
            throw BusinessException.forbidden("You are not permitted to access agent " + agent.getAgentCode());
        }
    }

    public boolean canViewAgent(UserPrincipal principal, UUID agentId, UUID agentAreaId) {
        return switch (principal.getRole()) {
            case AGENT -> agentId.equals(principal.getAgentId());
            case AREA_MANAGER -> agentAreaId != null && agentAreaId.equals(principal.getAreaId());
            case RISK_OFFICER, FINANCE_OFFICER, ADMIN -> true;
        };
    }

    public void assertAreaManagerOf(UserPrincipal principal, Agent agent) {
        if (principal.getRole() != Role.AREA_MANAGER || !agent.getArea().getId().equals(principal.getAreaId())) {
            throw BusinessException.forbidden("Agent " + agent.getAgentCode() + " is not assigned to your area");
        }
    }

    public UUID requireAgentId(UserPrincipal principal) {
        if (principal.getRole() != Role.AGENT || principal.getAgentId() == null) {
            throw BusinessException.forbidden("This operation is only available to agents");
        }
        return principal.getAgentId();
    }

    public UUID requireAreaId(UserPrincipal principal) {
        if (principal.getRole() != Role.AREA_MANAGER || principal.getAreaId() == null) {
            throw BusinessException.forbidden("This operation is only available to area managers with an assigned area");
        }
        return principal.getAreaId();
    }

    public boolean canAccessArea(UserPrincipal principal, UUID areaId) {
        return principal.isStaff() || (principal.getRole() == Role.AREA_MANAGER && areaId.equals(principal.getAreaId()));
    }
}
