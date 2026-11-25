package com.agentbanking.platform;

import com.agentbanking.platform.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DemoResetIntegrationTest extends IntegrationTestBase {

    @Test
    void resetRequiresConfirmationAndRebuildsTheDemoDatasetThroughTheLedger() {
        TestAgent extra = createAgent("HRE-METRO", "1000", "500");
        String admin = login("admin");

        JsonNode rejected = post(admin, "/api/v1/admin/demo/reset", Map.of("confirm", "yes"), 400);
        assertThat(rejected.get("errorCode").asText()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(call(HttpMethod.POST, "/api/v1/admin/demo/reset", login("finance"), Map.of("confirm", "RESET"), null)
                .getStatusCode().value()).isEqualTo(403);

        JsonNode result = post(admin, "/api/v1/admin/demo/reset", Map.of("confirm", "RESET"), 200);
        JsonNode seed = result.get("seed");
        assertThat(seed.get("agents").asInt()).isEqualTo(5);
        assertThat(seed.get("transactionsCompleted").asInt()).isGreaterThan(20);
        assertThat(seed.get("investigations").asInt()).isEqualTo(1);

        assertThat(call(HttpMethod.POST, "/api/v1/auth/login", null,
                Map.of("username", extra.username(), "password", PASSWORD), null).getStatusCode().value()).isEqualTo(401);

        String finance = login("finance");
        JsonNode agents = get(finance, "/api/v1/agents");
        assertThat(agents.size()).isEqualTo(5);
        JsonNode trialBalance = get(finance, "/api/v1/finance/reports/trial-balance");
        assertThat(trialBalance.get("balanced").asBoolean()).isTrue();
        assertThat(money(trialBalance.get("totalDebit"))).isEqualByComparingTo(money(trialBalance.get("totalCredit")));

        JsonNode investigations = get(login("risk"), "/api/v1/risk/investigations");
        assertThat(investigations.get("content").get(0).get("caseNumber").asText()).endsWith("-00001");
    }
}
