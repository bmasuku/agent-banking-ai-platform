package com.agentbanking.platform.auth;

import com.agentbanking.platform.audit.AuditService;
import com.agentbanking.platform.config.AppProperties;
import com.agentbanking.platform.user.UserRepository;
import com.agentbanking.platform.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/** Records failed logins in an independent transaction so the counter survives the authentication failure. */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AppProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailure(UUID userId, String ip) {
        return userRepository.findById(userId).map(user -> {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            boolean locked = false;
            if (user.getFailedLoginAttempts() >= properties.getSecurity().getMaxFailedLogins()
                    && user.getStatus() == UserStatus.ACTIVE) {
                user.setStatus(UserStatus.LOCKED);
                locked = true;
            }
            auditService.recordAs(user.getId(), user.getUsername(), user.getRole().name(),
                    locked ? "ACCOUNT_LOCKED" : "LOGIN_FAILED", "USER", user.getId().toString(),
                    Map.of("failedAttempts", user.getFailedLoginAttempts()), ip);
            return locked;
        }).orElse(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUnknownUser(String username, String ip) {
        auditService.recordAs(null, username == null ? "unknown" : username, "ANONYMOUS", "LOGIN_FAILED",
                "USER", null, Map.of("reason", "UNKNOWN_USER"), ip);
    }
}
