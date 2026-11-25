package com.agentbanking.platform;

import com.agentbanking.platform.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Read models and staff workflows: area manager monitoring, finance reports, risk review, simulations, demo reset. */
class OperationsWorkflowIntegrationTest extends IntegrationTestBase {

    @Test
    void areaManagerMonitoringEndpointsReturnLedgerDerivedData() {
        String manager = login("manager");
        JsonNode area = get(manager, "/api/v1/area-manager/area");
        assertThat(area.get("code").asText()).isEqualTo("HRE-METRO");

        JsonNode agents = get(manager, "/api/v1/area-manager/agents");
        assertThat(agents.size()).isGreaterThanOrEqualTo(5);
        JsonNode locations = get(manager, "/api/v1/area-manager/locations");
        assertThat(locations.get(0).get("latitude").isNumber()).isTrue();

        JsonNode predictions = get(manager, "/api/v1/area-manager/predictions");
        assertThat(predictions.size()).isEqualTo(agents.size());
        JsonNode p = predictions.get(0);
        assertThat(p.get("trajectory").size()).isGreaterThan(0);
        assertThat(p.get("explanation").asText()).isNotBlank();
        assertThat(p.get("modelSource").asText()).isEqualTo("RULES_FALLBACK");

        JsonNode report = get(manager, "/api/v1/area-manager/reports/area");
        assertThat(report.get("areaCode").asText()).isEqualTo("HRE-METRO");
        get(manager, "/api/v1/area-manager/exchanges");
        get(manager, "/api/v1/area-manager/alerts");
    }

    @Test
    void financeReportsAreAvailable() {
        String finance = login("finance");
        JsonNode income = get(finance, "/api/v1/finance/reports/income-statement");
        assertThat(income.has("feeIncome")).isTrue();
        JsonNode position = get(finance, "/api/v1/finance/reports/float-position");
        assertThat(position.get("areas").size()).isGreaterThanOrEqualTo(2);
        get(finance, "/api/v1/finance/reports/advance-book");
        get(finance, "/api/v1/finance/reports/commissions");
        get(finance, "/api/v1/finance/reports/transactions");
        JsonNode advances = get(finance, "/api/v1/finance/advances");
        assertThat(advances.get("totalElements").asLong()).isGreaterThan(0);
        JsonNode disbursements = get(finance, "/api/v1/finance/disbursements");
        assertThat(disbursements.get("totalElements").asLong()).isGreaterThan(0);
    }

    @Test
    void creditScoreIsExplainable() {
        String finance = login("finance");
        JsonNode agents = get(finance, "/api/v1/agents");
        String agentId = agents.get(0).get("id").asText();
        JsonNode score = post(finance, "/api/v1/credit-scores/agents/" + agentId + "/evaluate", null, 200);
        assertThat(score.get("score").asInt()).isBetween(0, 1000);
        assertThat(score.get("factors").size()).isGreaterThan(0);
        assertThat(score.get("explanation").asText()).contains("Score");
        assertThat(get(finance, "/api/v1/credit-scores/agents/" + agentId + "/history").get("totalElements").asLong()).isGreaterThan(0);
    }

    @Test
    void riskOfficerCanReviewFlaggedTransaction() {
        String risk = login("risk");
        JsonNode flagged = get(risk, "/api/v1/risk/flagged-transactions?reviewStatus=PENDING_REVIEW");
        if (flagged.get("totalElements").asLong() == 0) {
            return;
        }
        String checkId = flagged.get("content").get(0).get("fraudCheckId").asText();
        JsonNode detail = get(risk, "/api/v1/risk/fraud-checks/" + checkId);
        assertThat(detail.get("featureContributions").size()).isGreaterThan(0);

        JsonNode reviewed = post(risk, "/api/v1/risk/fraud-checks/" + checkId + "/review",
                Map.of("status", "CLEARED", "note", "Customer verified by phone"), 200);
        assertThat(reviewed.get("summary").get("reviewStatus").asText()).isEqualTo("CLEARED");
    }

    @Test
    void simulationRunsThroughTheRealPipelineAndKeepsLedgerBalanced() {
        String admin = login("admin");
        JsonNode run = post(admin, "/api/v1/admin/simulations",
                Map.of("scenario", "MIXED_TRADING_HOUR", "transactionCount", 10, "useAi", false, "randomSeed", 42), 201);
        assertThat(run.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(run.get("transactionsAttempted").asInt()).isEqualTo(10);
        assertThat(run.get("transactionsCompleted").asInt()).isGreaterThan(0);

        JsonNode tb = get(login("finance"), "/api/v1/finance/reports/trial-balance");
        assertThat(tb.get("balanced").asBoolean()).isTrue();
        assertThat(get(admin, "/api/v1/admin/simulations").get("totalElements").asLong()).isGreaterThan(0);
    }

    @Test
    void adminCanManageSettingsAndTariffsWithValidation() {
        String admin = login("admin");
        JsonNode settings = get(admin, "/api/v1/admin/settings");
        assertThat(settings.findValuesAsText("key")).contains("commission.recovery.max_percent", "commission.recovery.order");
        JsonNode error = expect(call(HttpMethod.PUT, "/api/v1/admin/settings/commission.recovery.max_percent", admin,
                Map.of("value", "150"), null), 400);
        assertThat(error.get("errorCode").asText()).isEqualTo("INVALID_SETTING_VALUE");

        JsonNode tariffs = get(admin, "/api/v1/admin/tariffs");
        assertThat(tariffs.size()).isGreaterThanOrEqualTo(5);
        JsonNode overlap = expect(call(HttpMethod.POST, "/api/v1/admin/tariffs", admin, Map.of(
                "transactionType", "CASH_DEPOSIT", "minAmount", new BigDecimal("1"), "maxAmount", new BigDecimal("50"),
                "feePercent", BigDecimal.ZERO, "feeFlat", BigDecimal.ZERO, "commissionPercent", BigDecimal.ONE,
                "commissionFlat", BigDecimal.ZERO), null), 409);
        assertThat(overlap.get("errorCode").asText()).isEqualTo("TARIFF_OVERLAP");

        JsonNode ai = get(admin, "/api/v1/admin/ai/status");
        assertThat(ai.get("activeSource").asText()).isEqualTo("RULES_FALLBACK");
    }
}
