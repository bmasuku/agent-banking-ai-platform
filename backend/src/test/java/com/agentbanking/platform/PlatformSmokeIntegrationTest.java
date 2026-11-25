package com.agentbanking.platform;

import com.agentbanking.platform.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformSmokeIntegrationTest extends IntegrationTestBase {

    @Test
    void openApiDocumentationIsPublishedWithAllModules() {
        ResponseEntity<JsonNode> docs = call(HttpMethod.GET, "/v3/api-docs", null, null, null);
        assertThat(docs.getStatusCode().value()).isEqualTo(200);
        JsonNode paths = docs.getBody().get("paths");
        assertThat(paths.has("/api/v1/auth/login")).isTrue();
        assertThat(paths.has("/api/v1/agent/transactions/deposit")).isTrue();
        assertThat(paths.has("/api/v1/area-manager/agents/{agentId}/replenishments")).isTrue();
        assertThat(paths.has("/api/v1/finance/commissions/agents/{agentId}/disburse")).isTrue();
        assertThat(paths.has("/api/v1/risk/investigations")).isTrue();
        assertThat(paths.has("/api/v1/admin/demo/reset")).isTrue();
        assertThat(call(HttpMethod.GET, "/v3/api-docs/mobile-agent-area-manager", null, null, null).getStatusCode().value()).isEqualTo(200);
        assertThat(call(HttpMethod.GET, "/v3/api-docs/web-risk-finance-admin", null, null, null).getStatusCode().value()).isEqualTo(200);
        assertThat(rest.getForEntity("/swagger-ui/index.html", String.class).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void demoDatasetIsSeededThroughTheLedger() {
        String finance = login("finance");
        JsonNode agents = get(finance, "/api/v1/agents");
        assertThat(agents.size()).isGreaterThanOrEqualTo(5);
        assertThat(agents.findValuesAsText("agentCode")).contains("AGT-001", "AGT-002", "AGT-003", "AGT-004", "AGT-005");

        JsonNode trialBalance = get(finance, "/api/v1/finance/reports/trial-balance");
        assertThat(trialBalance.get("balanced").asBoolean()).isTrue();
        assertThat(money(trialBalance.get("totalDebit"))).isEqualByComparingTo(money(trialBalance.get("totalCredit")));
        assertThat(money(trialBalance.get("journalDebits"))).isEqualByComparingTo(money(trialBalance.get("journalCredits")));

        JsonNode transactions = get(finance, "/api/v1/transactions?size=5");
        assertThat(transactions.get("totalElements").asLong()).isGreaterThan(20);

        for (String username : new String[]{"agent1", "agent2", "agent3", "agent4", "agent5", "manager", "risk", "admin"}) {
            assertThat(login(username)).isNotBlank();
        }
        JsonNode balances = get(login("agent1"), "/api/v1/agent/balances").get("balances");
        assertThat(money(balances.get("efloat"))).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void seededStructuringBurstIsFlaggedAndUnderInvestigation() {
        String risk = login("risk");
        JsonNode flagged = get(risk, "/api/v1/risk/flagged-transactions?size=50");
        assertThat(flagged.get("totalElements").asLong()).isGreaterThan(0);
        assertThat(flagged.get("content").findValuesAsText("agentCode")).contains("AGT-005");
        JsonNode first = flagged.get("content").get(0);
        assertThat(first.get("reasonCodes").size()).isGreaterThan(0);

        JsonNode investigations = get(risk, "/api/v1/risk/investigations");
        assertThat(investigations.get("content").findValuesAsText("agentCode")).contains("AGT-005");
        JsonNode agents = get(risk, "/api/v1/risk/agents");
        JsonNode agt5 = null;
        for (JsonNode a : agents) {
            if (a.get("agentCode").asText().equals("AGT-005")) {
                agt5 = a;
            }
        }
        assertThat(agt5).isNotNull();
        assertThat(agt5.get("status").asText()).isEqualTo("UNDER_REVIEW");
        assertThat(agt5.get("openInvestigations").asLong()).isEqualTo(1);
    }
}
