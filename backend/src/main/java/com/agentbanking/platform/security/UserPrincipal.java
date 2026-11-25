package com.agentbanking.platform.security;

import com.agentbanking.platform.user.Role;
import com.agentbanking.platform.user.User;
import com.agentbanking.platform.user.UserStatus;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String username;
    private final String fullName;
    private final String passwordHash;
    private final Role role;
    private final UserStatus status;
    private final UUID areaId;
    private final UUID agentId;
    private final String tokenId;
    private final Instant tokenExpiresAt;

    public UserPrincipal(UUID id, String username, String fullName, String passwordHash, Role role, UserStatus status,
                         UUID areaId, UUID agentId, String tokenId, Instant tokenExpiresAt) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.areaId = areaId;
        this.agentId = agentId;
        this.tokenId = tokenId;
        this.tokenExpiresAt = tokenExpiresAt;
    }

    public static UserPrincipal from(User user, UUID agentId, UUID agentAreaId) {
        return from(user, agentId, agentAreaId, null, null);
    }

    public static UserPrincipal from(User user, UUID agentId, UUID agentAreaId, String tokenId, Instant tokenExpiresAt) {
        UUID areaId = agentAreaId != null ? agentAreaId : (user.getArea() != null ? user.getArea().getId() : null);
        return new UserPrincipal(user.getId(), user.getUsername(), user.getFullName(), user.getPasswordHash(),
                user.getRole(), user.getStatus(), areaId, agentId, tokenId, tokenExpiresAt);
    }

    public boolean hasRole(Role candidate) {
        return role == candidate;
    }

    public boolean isStaff() {
        return role == Role.RISK_OFFICER || role == Role.FINANCE_OFFICER || role == Role.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.LOCKED;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
