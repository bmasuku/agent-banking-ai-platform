package com.agentbanking.platform.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack integration tests: real PostgreSQL (Testcontainers), Flyway migrations, the seeded demo
 * dataset and HTTP calls through Spring Security. The Spring context (and container) is shared.
 */
@Tag("integration")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    public static final String PASSWORD = "Demo@2026!";
    private static final AtomicInteger AGENT_COUNTER = new AtomicInteger();

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected ObjectMapper objectMapper;

    public record TestAgent(UUID id, String code, String username, String token) {
    }

    protected String login(String username) {
        return login(username, PASSWORD);
    }

    protected String login(String username, String password) {
        ResponseEntity<JsonNode> response = call(HttpMethod.POST, "/api/v1/auth/login", null,
                Map.of("username", username, "password", password), null);
        assertThat(response.getStatusCode().value()).as("login %s: %s", username, response.getBody()).isEqualTo(200);
        return response.getBody().get("accessToken").asText();
    }

    protected ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    protected JsonNode get(String token, String path) {
        return expect(call(HttpMethod.GET, path, token, null, null), 200);
    }

    protected JsonNode post(String token, String path, Object body, int expectedStatus) {
        return expect(call(HttpMethod.POST, path, token, body, null), expectedStatus);
    }

    protected JsonNode postIdempotent(String token, String path, Object body, int expectedStatus) {
        return expect(call(HttpMethod.POST, path, token, body, newKey()), expectedStatus);
    }

    protected JsonNode expect(ResponseEntity<JsonNode> response, int expectedStatus) {
        assertThat(response.getStatusCode().value()).as("unexpected status, body: %s", response.getBody()).isEqualTo(expectedStatus);
        return response.getBody();
    }

    protected static String newKey() {
        return "it-" + UUID.randomUUID();
    }

    protected static BigDecimal money(JsonNode node) {
        return node.decimalValue().setScale(2, RoundingMode.HALF_EVEN);
    }

    protected UUID areaId(String adminToken, String code) {
        for (JsonNode area : get(adminToken, "/api/v1/areas")) {
            if (area.get("code").asText().equals(code)) {
                return UUID.fromString(area.get("id").asText());
            }
        }
        throw new IllegalStateException("Area not found: " + code);
    }

    /** Onboards a fresh agent in the given area and (for HRE-METRO) has the seeded area manager replenish it. */
    protected TestAgent createAgent(String areaCode, String efloat, String cash) {
        String admin = login("admin");
        int n = AGENT_COUNTER.incrementAndGet();
        String username = "itagent" + n + "x" + UUID.randomUUID().toString().substring(0, 6);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("email", username + "@test.local");
        body.put("fullName", "Integration Agent " + n);
        body.put("phoneNumber", "+26377" + String.format("%07d", 9_000_000 + (int) (Math.random() * 999_999)));
        body.put("password", PASSWORD);
        body.put("businessName", "IT Store " + n);
        body.put("areaId", areaId(admin, areaCode));
        body.put("latitude", -17.83);
        body.put("longitude", 31.05);
        body.put("address", "Test Street " + n);
        body.put("minEfloatThreshold", 100);
        body.put("minCashThreshold", 100);
        body.put("dailyTransactionLimit", 50000);
        JsonNode profile = post(admin, "/api/v1/admin/agents", body, 201);
        UUID agentId = UUID.fromString(profile.get("id").asText());

        if ("HRE-METRO".equals(areaCode)) {
            String manager = login("manager");
            if (new BigDecimal(efloat).signum() > 0) {
                postIdempotent(manager, "/api/v1/area-manager/agents/" + agentId + "/replenishments",
                        Map.of("type", "EFLOAT", "amount", new BigDecimal(efloat), "reason", "test funding"), 201);
            }
            if (new BigDecimal(cash).signum() > 0) {
                postIdempotent(manager, "/api/v1/area-manager/agents/" + agentId + "/replenishments",
                        Map.of("type", "CASH", "amount", new BigDecimal(cash), "reason", "test funding"), 201);
            }
        }
        return new TestAgent(agentId, profile.get("agentCode").asText(), username, login(username));
    }

    protected JsonNode balances(TestAgent agent) {
        return get(agent.token(), "/api/v1/agent/balances").get("balances");
    }

    protected Map<String, Object> customerTx(String amount, String phone, String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", new BigDecimal(amount));
        body.put("customerPhone", phone);
        body.put("customerName", name);
        return body;
    }
}
