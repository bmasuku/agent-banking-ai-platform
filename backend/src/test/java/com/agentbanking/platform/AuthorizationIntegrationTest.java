package com.agentbanking.platform;

import com.agentbanking.platform.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationIntegrationTest extends IntegrationTestBase {

    @Test
    void unauthenticatedRequestsAreRejected() {
        JsonNode error = expect(call(HttpMethod.GET, "/api/v1/agent/balances", null, null, null), 401);
        assertThat(error.get("errorCode").asText()).isEqualTo("UNAUTHORIZED");
        expect(call(HttpMethod.GET, "/api/v1/agent/balances", "not-a-jwt", null, null), 401);
    }

    @Test
    void rolesAreEnforcedByTheBackend() {
        String agent = login("agent1");
        String risk = login("risk");
        String finance = login("finance");
        String manager = login("manager");

        expect(call(HttpMethod.GET, "/api/v1/finance/commissions", agent, null, null), 403);
        expect(call(HttpMethod.GET, "/api/v1/risk/dashboard", agent, null, null), 403);
        expect(call(HttpMethod.GET, "/api/v1/finance/advances", risk, null, null), 403);
        expect(call(HttpMethod.GET, "/api/v1/admin/settings", finance, null, null), 403);
        expect(call(HttpMethod.GET, "/api/v1/agent/balances", manager, null, null), 403);
        expect(call(HttpMethod.GET, "/api/v1/agents", manager, null, null), 403);
        expect(call(HttpMethod.POST, "/api/v1/admin/demo/reset", finance, Map.of("confirm", "RESET"), null), 403);

        get(risk, "/api/v1/risk/dashboard");
        get(finance, "/api/v1/finance/reports/float-position");
        get(manager, "/api/v1/area-manager/agents");
        get(login("admin"), "/api/v1/risk/dashboard");
    }

    @Test
    void areaManagerIsRestrictedToOwnArea() {
        TestAgent harareAgent = createAgent("HRE-METRO", "100", "0");
        TestAgent bulawayoAgent = createAgent("BYO-METRO", "0", "0");
        String manager = login("manager");
        String admin = login("admin");

        get(manager, "/api/v1/area-manager/agents/" + harareAgent.id());
        expect(call(HttpMethod.GET, "/api/v1/area-manager/agents/" + bulawayoAgent.id(), manager, null, null), 403);
        expect(call(HttpMethod.POST, "/api/v1/area-manager/agents/" + bulawayoAgent.id() + "/replenishments", manager,
                Map.of("type", "EFLOAT", "amount", new BigDecimal("10.00")), newKey()), 403);
        expect(call(HttpMethod.GET, "/api/v1/transactions?areaId=" + areaId(admin, "BYO-METRO"), manager, null, null), 403);
        expect(call(HttpMethod.GET, "/api/v1/predictions/agents/" + bulawayoAgent.id(), manager, null, null), 403);

        JsonNode myAgents = get(manager, "/api/v1/area-manager/agents");
        assertThat(myAgents.findValuesAsText("agentCode")).contains(harareAgent.code()).doesNotContain(bulawayoAgent.code());
    }

    @Test
    void agentsCanOnlySeeTheirOwnData() {
        TestAgent alice = createAgent("HRE-METRO", "500", "0");
        TestAgent bob = createAgent("HRE-METRO", "500", "0");
        JsonNode bobTx = postIdempotent(bob.token(), "/api/v1/agent/transactions/transfer",
                customerTx("50", FinancialScenariosIntegrationTest.uniquePhone(), "Bob Customer"), 201);

        expect(call(HttpMethod.GET, "/api/v1/agent/transactions/" + bobTx.get("id").asText(), alice.token(), null, null), 403);
        expect(call(HttpMethod.GET, "/api/v1/credit-scores/agents/" + bob.id(), alice.token(), null, null), 403);
        expect(call(HttpMethod.POST, "/api/v1/predictions/agents/" + bob.id(), alice.token(), null, null), 403);
        JsonNode prediction = expect(call(HttpMethod.POST, "/api/v1/predictions/agents/" + alice.id(), alice.token(), null, null), 200);
        assertThat(prediction.get("explanation").asText()).isNotBlank();

        JsonNode mine = get(alice.token(), "/api/v1/agent/transactions");
        assertThat(mine.get("content").findValuesAsText("reference")).doesNotContain(bobTx.get("reference").asText());
    }

    @Test
    void loginRefreshAndLogoutLifecycle() {
        TestAgent agent = createAgent("HRE-METRO", "0", "0");
        expect(call(HttpMethod.POST, "/api/v1/auth/login", null, Map.of("username", agent.username(), "password", "wrong-pass1"), null), 401);

        JsonNode tokens = expect(call(HttpMethod.POST, "/api/v1/auth/login", null,
                Map.of("username", agent.username(), "password", PASSWORD), null), 200);
        String access = tokens.get("accessToken").asText();
        String refresh = tokens.get("refreshToken").asText();
        JsonNode me = get(access, "/api/v1/auth/me");
        assertThat(me.get("role").asText()).isEqualTo("AGENT");
        assertThat(me.get("agentId").asText()).isEqualTo(agent.id().toString());

        JsonNode rotated = expect(call(HttpMethod.POST, "/api/v1/auth/refresh", null, Map.of("refreshToken", refresh), null), 200);
        assertThat(rotated.get("refreshToken").asText()).isNotEqualTo(refresh);
        expect(call(HttpMethod.POST, "/api/v1/auth/refresh", null, Map.of("refreshToken", refresh), null), 401);

        String newAccess = rotated.get("accessToken").asText();
        expect(call(HttpMethod.POST, "/api/v1/auth/logout", newAccess, Map.of("refreshToken", rotated.get("refreshToken").asText()), null), 204);
        expect(call(HttpMethod.GET, "/api/v1/auth/me", newAccess, null, null), 401);
        expect(call(HttpMethod.POST, "/api/v1/auth/refresh", null, Map.of("refreshToken", rotated.get("refreshToken").asText()), null), 401);
    }
}
