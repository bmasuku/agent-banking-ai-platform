package com.agentbanking.platform.security;

import com.agentbanking.platform.config.AppProperties;
import com.agentbanking.platform.user.Role;
import com.agentbanking.platform.user.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("unit-test-secret-key-that-is-at-least-256-bits-long".getBytes(StandardCharsets.UTF_8));

    private static JwtService service(String secret, String issuer, Duration ttl) {
        AppProperties props = new AppProperties();
        props.getSecurity().getJwt().setSecret(secret);
        props.getSecurity().getJwt().setIssuer(issuer);
        props.getSecurity().getJwt().setAccessTokenTtl(ttl);
        return new JwtService(props);
    }

    private static UserPrincipal agent() {
        return new UserPrincipal(UUID.randomUUID(), "agent1", "John Mwangi", "x", Role.AGENT, UserStatus.ACTIVE,
                UUID.randomUUID(), UUID.randomUUID(), null, null);
    }

    @Test
    void issuedTokenRoundTripsClaims() {
        JwtService jwt = service(SECRET, "test-issuer", Duration.ofMinutes(5));
        UserPrincipal principal = agent();
        JwtService.IssuedToken token = jwt.issueAccessToken(principal);

        Claims claims = jwt.parseAccessToken(token.token());
        assertThat(claims.getSubject()).isEqualTo(principal.getId().toString());
        assertThat(claims.getId()).isEqualTo(token.jti());
        assertThat(claims.get(JwtService.CLAIM_ROLE, String.class)).isEqualTo("AGENT");
        assertThat(claims.get(JwtService.CLAIM_AGENT, String.class)).isEqualTo(principal.getAgentId().toString());
        assertThat(claims.get(JwtService.CLAIM_AREA, String.class)).isEqualTo(principal.getAreaId().toString());
        assertThat(token.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void rejectsTokensSignedWithAnotherKeyOrIssuer() {
        JwtService jwt = service(SECRET, "test-issuer", Duration.ofMinutes(5));
        String otherSecret = Base64.getEncoder()
                .encodeToString("another-secret-key-that-is-also-256-bits-or-more".getBytes(StandardCharsets.UTF_8));
        String foreign = service(otherSecret, "test-issuer", Duration.ofMinutes(5)).issueAccessToken(agent()).token();
        String wrongIssuer = service(SECRET, "someone-else", Duration.ofMinutes(5)).issueAccessToken(agent()).token();

        assertThatThrownBy(() -> jwt.parseAccessToken(foreign)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwt.parseAccessToken(wrongIssuer)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwt.parseAccessToken(foreign.substring(0, foreign.length() - 4) + "abcd"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredAndNonAccessTokens() {
        JwtService jwt = service(SECRET, "test-issuer", Duration.ofMinutes(5));
        var key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));
        String expired = Jwts.builder().issuer("test-issuer").subject("x").claim(JwtService.CLAIM_TYPE, "access")
                .expiration(Date.from(Instant.now().minusSeconds(60))).signWith(key).compact();
        String refreshTyped = Jwts.builder().issuer("test-issuer").subject("x").claim(JwtService.CLAIM_TYPE, "refresh")
                .expiration(Date.from(Instant.now().plusSeconds(60))).signWith(key).compact();

        assertThatThrownBy(() -> jwt.parseAccessToken(expired)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwt.parseAccessToken(refreshTyped)).isInstanceOf(JwtException.class);
    }

    @Test
    void refusesWeakSecrets() {
        String weak = Base64.getEncoder().encodeToString("short".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service(weak, "i", Duration.ofMinutes(1))).isInstanceOf(IllegalStateException.class);
    }
}
