package com.agentbanking.platform.auth;

import com.agentbanking.platform.agent.AgentRepository;
import com.agentbanking.platform.audit.AuditService;
import com.agentbanking.platform.auth.AuthDtos.AuthenticatedUser;
import com.agentbanking.platform.auth.AuthDtos.TokenResponse;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.Hashing;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.security.JwtService;
import com.agentbanking.platform.security.PrincipalLoader;
import com.agentbanking.platform.security.UserPrincipal;
import com.agentbanking.platform.user.User;
import com.agentbanking.platform.user.UserRepository;
import com.agentbanking.platform.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final AgentRepository agentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RevokedAccessTokenRepository revokedAccessTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PrincipalLoader principalLoader;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public TokenResponse login(String username, String password, String ip) {
        Optional<User> found = userRepository.findByUsernameIgnoreCase(username.trim())
                .or(() -> userRepository.findByEmailIgnoreCase(username.trim()));
        if (found.isEmpty()) {
            loginAttemptService.recordUnknownUser(username, ip);
            throw invalidCredentials();
        }
        User user = found.get();
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new BusinessException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "Account is locked after repeated failed logins. Contact an administrator.");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "Account is suspended");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            boolean locked = loginAttemptService.recordFailure(user.getId(), ip);
            if (locked) {
                throw new BusinessException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                        "Account is locked after repeated failed logins. Contact an administrator.");
            }
            throw invalidCredentials();
        }
        return transactionTemplate.execute(status -> completeLogin(user.getId(), ip));
    }

    private TokenResponse completeLogin(UUID userId, String ip) {
        User user = userRepository.findById(userId).orElseThrow(this::invalidCredentials);
        user.setFailedLoginAttempts(0);
        user.setLastLoginAt(AppClock.now());
        userRepository.save(user);
        TokenResponse response = issueTokens(user, UUID.randomUUID(), ip);
        auditService.recordAs(user.getId(), user.getUsername(), user.getRole().name(), "LOGIN_SUCCESS", "USER",
                user.getId().toString(), Map.of(), ip);
        return response;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse refresh(String rawRefreshToken, String ip) {
        Instant now = AppClock.now();
        RefreshToken token = refreshTokenRepository.findByTokenHash(Hashing.sha256Hex(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid"));
        if (token.getRevokedAt() != null) {
            // A rotated token was presented again: treat as theft and revoke the whole session family.
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now, "REUSE_DETECTED");
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSED",
                    "Refresh token was already used. All sessions in this family have been revoked.");
        }
        if (!token.getExpiresAt().isAfter(now)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_EXPIRED", "Refresh token has expired");
        }
        User user = token.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            refreshTokenRepository.revokeFamily(token.getFamilyId(), now, "ACCOUNT_" + user.getStatus());
            throw new BusinessException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE", "Account is not active");
        }
        token.setRevokedAt(now);
        token.setRevokedReason("ROTATED");
        return issueTokens(user, token.getFamilyId(), ip);
    }

    @Transactional
    public void logout(UserPrincipal principal, String rawRefreshToken, boolean allSessions) {
        Instant now = AppClock.now();
        if (principal.getTokenId() != null && !revokedAccessTokenRepository.existsById(principal.getTokenId())) {
            revokedAccessTokenRepository.save(new RevokedAccessToken(principal.getTokenId(), principal.getId(),
                    principal.getTokenExpiresAt() != null ? principal.getTokenExpiresAt() : now.plusSeconds(jwtService.accessTokenTtlSeconds()), now));
        }
        if (allSessions || rawRefreshToken == null || rawRefreshToken.isBlank()) {
            refreshTokenRepository.revokeAllForUser(principal.getId(), now, "LOGOUT");
        } else {
            refreshTokenRepository.findByTokenHash(Hashing.sha256Hex(rawRefreshToken))
                    .filter(t -> t.getUser().getId().equals(principal.getId()))
                    .ifPresent(t -> refreshTokenRepository.revokeFamily(t.getFamilyId(), now, "LOGOUT"));
        }
        auditService.record("LOGOUT", "USER", principal.getId(), Map.of("allSessions", allSessions));
    }

    @Transactional
    public void changePassword(UserPrincipal principal, String currentPassword, String newPassword) {
        User user = userRepository.findById(principal.getId()).orElseThrow(this::invalidCredentials);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", "Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw BusinessException.badRequest("PASSWORD_REUSE", "New password must differ from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(AppClock.now());
        refreshTokenRepository.revokeAllForUser(user.getId(), AppClock.now(), "PASSWORD_CHANGED");
        auditService.record("PASSWORD_CHANGED", "USER", user.getId(), Map.of());
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser me(UserPrincipal principal) {
        User user = userRepository.findById(principal.getId()).orElseThrow(this::invalidCredentials);
        return toAuthenticatedUser(user, principalLoader.build(user, null, null));
    }

    private TokenResponse issueTokens(User user, UUID familyId, String ip) {
        UserPrincipal principal = principalLoader.build(user, null, null);
        JwtService.IssuedToken access = jwtService.issueAccessToken(principal);
        String rawRefresh = newOpaqueToken();
        RefreshToken refresh = new RefreshToken();
        refresh.setUser(user);
        refresh.setTokenHash(Hashing.sha256Hex(rawRefresh));
        refresh.setFamilyId(familyId);
        refresh.setExpiresAt(jwtService.refreshTokenExpiry());
        refresh.setCreatedByIp(ip);
        refreshTokenRepository.save(refresh);
        return new TokenResponse(access.token(), rawRefresh, "Bearer", jwtService.accessTokenTtlSeconds(),
                jwtService.refreshTokenTtlSeconds(), toAuthenticatedUser(user, principal));
    }

    private AuthenticatedUser toAuthenticatedUser(User user, UserPrincipal principal) {
        String agentCode = principal.getAgentId() == null ? null
                : agentRepository.findById(principal.getAgentId()).map(a -> a.getAgentCode()).orElse(null);
        String areaName = user.getArea() != null ? user.getArea().getName() : null;
        if (areaName == null && principal.getAgentId() != null) {
            areaName = agentRepository.findById(principal.getAgentId()).map(a -> a.getArea().getName()).orElse(null);
        }
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(), user.getRole(),
                principal.getAreaId(), areaName, principal.getAgentId(), agentCode,
                user.getRole().isMobileRole() ? "MOBILE" : "WEB");
    }

    private String newOpaqueToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
    }
}
