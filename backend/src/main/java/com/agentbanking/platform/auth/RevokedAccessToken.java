package com.agentbanking.platform.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Deny-list entry for access tokens invalidated by logout before their natural expiry. */
@Entity
@Table(name = "revoked_access_tokens")
@Getter
@NoArgsConstructor
public class RevokedAccessToken {

    @Id
    @Column(length = 64)
    private String jti;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant revokedAt;

    public RevokedAccessToken(String jti, UUID userId, Instant expiresAt, Instant revokedAt) {
        this.jti = jti;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
    }
}
