package com.agentbanking.platform.security;

import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_AREA = "areaId";
    public static final String CLAIM_AGENT = "agentId";
    public static final String CLAIM_TYPE = "typ";
    private static final String ACCESS_TYPE = "access";

    private final SecretKey key;
    private final AppProperties.Jwt jwtProperties;

    public JwtService(AppProperties properties) {
        this.jwtProperties = properties.getSecurity().getJwt();
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.security.jwt.secret must decode to at least 256 bits");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public IssuedToken issueAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.getAccessTokenTtl());
        String jti = UUID.randomUUID().toString();
        var builder = Jwts.builder()
                .id(jti)
                .issuer(jwtProperties.getIssuer())
                .subject(principal.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_TYPE, ACCESS_TYPE)
                .claim(CLAIM_USERNAME, principal.getUsername())
                .claim(CLAIM_ROLE, principal.getRole().name());
        if (principal.getAreaId() != null) {
            builder.claim(CLAIM_AREA, principal.getAreaId().toString());
        }
        if (principal.getAgentId() != null) {
            builder.claim(CLAIM_AGENT, principal.getAgentId().toString());
        }
        return new IssuedToken(builder.signWith(key).compact(), jti, expiresAt);
    }

    /** Validates signature, issuer, expiry and token type. Throws JwtException when invalid. */
    public Claims parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(jwtProperties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!ACCESS_TYPE.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("Not an access token");
        }
        return claims;
    }

    public long accessTokenTtlSeconds() {
        return jwtProperties.getAccessTokenTtl().toSeconds();
    }

    public long refreshTokenTtlSeconds() {
        return jwtProperties.getRefreshTokenTtl().toSeconds();
    }

    public Instant refreshTokenExpiry() {
        return AppClock.now().plus(jwtProperties.getRefreshTokenTtl());
    }

    public record IssuedToken(String token, String jti, Instant expiresAt) {
    }
}
