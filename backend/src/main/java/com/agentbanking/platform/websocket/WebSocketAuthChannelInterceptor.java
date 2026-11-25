package com.agentbanking.platform.websocket;

import com.agentbanking.platform.agent.AgentRepository;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.security.AccessControlService;
import com.agentbanking.platform.security.JwtAuthenticationFilter;
import com.agentbanking.platform.security.UserPrincipal;
import com.agentbanking.platform.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Authenticates STOMP CONNECT frames with the JWT access token and authorizes every SUBSCRIBE
 * against the same ownership and area rules as the REST API.
 */
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AccessControlService accessControlService;
    private final AgentRepository agentRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new MessageDeliveryException("Missing Bearer token in STOMP CONNECT Authorization header");
            }
            UserPrincipal principal = jwtAuthenticationFilter.authenticate(header.substring(7).trim())
                    .orElseThrow(() -> new MessageDeliveryException("Invalid or expired access token"));
            accessor.setUser(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            if (!(accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth)
                    || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
                throw new MessageDeliveryException("Not authenticated");
            }
            if (!canSubscribe(principal, accessor.getDestination())) {
                throw new MessageDeliveryException("Subscription to " + accessor.getDestination() + " is not permitted");
            }
        }
        return message;
    }

    boolean canSubscribe(UserPrincipal principal, String destination) {
        if (destination == null) {
            return false;
        }
        if (destination.startsWith("/user/queue/")) {
            return true;
        }
        if (destination.startsWith("/topic/agents/")) {
            UUID agentId = parseId(destination, "/topic/agents/");
            if (agentId == null) {
                return false;
            }
            return agentRepository.findById(agentId)
                    .map(agent -> accessControlService.canViewAgent(principal, agent.getId(), agent.getArea().getId()))
                    .orElse(false);
        }
        if (destination.startsWith("/topic/areas/")) {
            UUID areaId = parseId(destination, "/topic/areas/");
            return areaId != null && accessControlService.canAccessArea(principal, areaId);
        }
        if (destination.startsWith(RealtimeEvent.Topics.RISK)) {
            return principal.getRole() == Role.RISK_OFFICER || principal.getRole() == Role.ADMIN;
        }
        if (destination.startsWith(RealtimeEvent.Topics.FINANCE)) {
            return principal.getRole() == Role.FINANCE_OFFICER || principal.getRole() == Role.ADMIN;
        }
        return false;
    }

    private UUID parseId(String destination, String prefix) {
        String rest = destination.substring(prefix.length());
        int slash = rest.indexOf('/');
        try {
            return UUID.fromString(slash >= 0 ? rest.substring(0, slash) : rest);
        } catch (IllegalArgumentException | BusinessException e) {
            return null;
        }
    }
}
