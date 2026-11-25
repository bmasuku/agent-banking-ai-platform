package com.agentbanking.platform;

import com.agentbanking.platform.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CommissionRecoveryIntegrationTest extends IntegrationTestBase {

    @Test
    void disbursementRecoversAdvanceWithinConfiguredCapAndOrder() {
        TestAgent agent = createAgent("HRE-METRO", "6000", "0");
        String finance = login("finance");
        String admin = login("admin");

        JsonNode advance = postIdempotent(agent.token(), "/api/v1/agent/advances",
                Map.of("amount", new BigDecimal("500.00"), "termDays", 7, "purpose", "Recovery test"), 201);
        if (advance.get("status").asText().equals("PENDING")) {
            advance = post(finance, "/api/v1/finance/advances/" + advance.get("id").asText() + "/approve", Map.of("note", "ok"), 200);
        }
        BigDecimal interest = money(advance.get("interestAmount"));
        assertThat(interest).isGreaterThan(new BigDecimal("5.00"));

        postIdempotent(agent.token(), "/api/v1/agent/transactions/transfer",
                customerTx("2000", FinancialScenariosIntegrationTest.uniquePhone(), "Commission A"), 201);
        postIdempotent(agent.token(), "/api/v1/agent/transactions/deposit",
                customerTx("1000", FinancialScenariosIntegrationTest.uniquePhone(), "Commission B"), 201);
        BigDecimal gross = money(balances(agent).get("commissionPayable"));
        assertThat(gross).isEqualByComparingTo("10.00");

        JsonNode preview = get(finance, "/api/v1/finance/commissions/agents/" + agent.id() + "/preview");
        assertThat(preview.get("eligible").asBoolean()).isTrue();
        assertThat(money(preview.get("recoveryCap"))).isEqualByComparingTo("5.00");
        assertThat(money(preview.get("interestRecovered"))).isEqualByComparingTo("5.00");
        assertThat(money(preview.get("principalRecovered"))).isEqualByComparingTo("0.00");
        assertThat(money(preview.get("netCommission"))).isEqualByComparingTo("5.00");

        BigDecimal efloatBefore = money(balances(agent).get("efloat"));
        JsonNode payout = postIdempotent(finance, "/api/v1/finance/commissions/agents/" + agent.id() + "/disburse", null, 201);
        assertThat(money(payout.get("grossCommission"))).isEqualByComparingTo("10.00");
        assertThat(money(payout.get("interestRecovered"))).isEqualByComparingTo("5.00");
        assertThat(money(payout.get("principalRecovered"))).isEqualByComparingTo("0.00");
        assertThat(money(payout.get("netCommission"))).isEqualByComparingTo("5.00");
        assertThat(money(payout.get("outstandingAdvanceAfter")))
                .isEqualByComparingTo(money(payout.get("outstandingAdvanceBefore")).subtract(new BigDecimal("5.00")));

        JsonNode b = balances(agent);
        assertThat(money(b.get("commissionPayable"))).isEqualByComparingTo("0.00");
        assertThat(money(b.get("advanceInterestOutstanding"))).isEqualByComparingTo(interest.subtract(new BigDecimal("5.00")));
        assertThat(money(b.get("advancePrincipalOutstanding"))).isEqualByComparingTo("500.00");
        assertThat(money(b.get("efloat"))).isEqualByComparingTo(efloatBefore.add(new BigDecimal("5.00")));

        try {
            updateSetting(admin, "commission.recovery.max_percent", "100");
            updateSetting(admin, "commission.recovery.order", "PRINCIPAL_FIRST");
            postIdempotent(agent.token(), "/api/v1/agent/transactions/deposit",
                    customerTx("2000", FinancialScenariosIntegrationTest.uniquePhone(), "Commission C"), 201);

            JsonNode second = postIdempotent(finance, "/api/v1/finance/commissions/agents/" + agent.id() + "/disburse", null, 201);
            assertThat(money(second.get("grossCommission"))).isEqualByComparingTo("10.00");
            assertThat(money(second.get("principalRecovered"))).isEqualByComparingTo("10.00");
            assertThat(money(second.get("interestRecovered"))).isEqualByComparingTo("0.00");
            assertThat(money(second.get("netCommission"))).isEqualByComparingTo("0.00");
            assertThat(money(balances(agent).get("advancePrincipalOutstanding"))).isEqualByComparingTo("490.00");
        } finally {
            updateSetting(admin, "commission.recovery.max_percent", "50");
            updateSetting(admin, "commission.recovery.order", "INTEREST_FIRST");
        }

        JsonNode detail = get(finance, "/api/v1/finance/advances/" + advance.get("id").asText());
        assertThat(detail.get("repayments").findValuesAsText("source")).containsOnly("COMMISSION_DEDUCTION");
        assertThat(detail.get("repayments").size()).isEqualTo(2);
    }

    @Test
    void disbursementBelowMinimumIsRejectedAndRetriesAreIdempotent() {
        TestAgent agent = createAgent("HRE-METRO", "2000", "0");
        String finance = login("finance");
        postIdempotent(agent.token(), "/api/v1/agent/transactions/transfer",
                customerTx("100", FinancialScenariosIntegrationTest.uniquePhone(), "Tiny"), 201);

        JsonNode error = postIdempotent(finance, "/api/v1/finance/commissions/agents/" + agent.id() + "/disburse", null, 422);
        assertThat(error.get("errorCode").asText()).isEqualTo("COMMISSION_BELOW_MINIMUM");

        postIdempotent(agent.token(), "/api/v1/agent/transactions/deposit",
                customerTx("1000", FinancialScenariosIntegrationTest.uniquePhone(), "Bigger"), 201);
        String key = newKey();
        JsonNode first = expect(call(HttpMethod.POST, "/api/v1/finance/commissions/agents/" + agent.id() + "/disburse", finance, null, key), 201);
        JsonNode replay = expect(call(HttpMethod.POST, "/api/v1/finance/commissions/agents/" + agent.id() + "/disburse", finance, null, key), 200);
        assertThat(replay.get("id").asText()).isEqualTo(first.get("id").asText());
        assertThat(money(first.get("netCommission"))).isEqualByComparingTo(money(first.get("grossCommission")));

        JsonNode summary = get(agent.token(), "/api/v1/agent/commissions/summary");
        assertThat(money(summary.get("accruedPayable"))).isEqualByComparingTo("0.00");
        assertThat(money(summary.get("lifetimeGrossDisbursed"))).isEqualByComparingTo(money(first.get("grossCommission")));
    }

    private void updateSetting(String adminToken, String key, String value) {
        expect(call(HttpMethod.PUT, "/api/v1/admin/settings/" + key, adminToken, Map.of("value", value), null), 200);
    }
}
