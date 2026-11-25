package com.agentbanking.platform;

import com.agentbanking.platform.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The six money-movement scenarios from the specification, verified against ledger-derived balances. */
class FinancialScenariosIntegrationTest extends IntegrationTestBase {

    @Test
    void agentSendsMoneyToCustomer_efloatDecreasesByAmount() {
        TestAgent agent = createAgent("HRE-METRO", "1000", "0");
        JsonNode tx = postIdempotent(agent.token(), "/api/v1/agent/transactions/transfer",
                customerTx("100", uniquePhone(), "Transfer Customer"), 201);

        assertThat(tx.get("status").asText()).isEqualTo("COMPLETED");
        assertBalancedJournal(tx);
        JsonNode b = balances(agent);
        assertThat(money(b.get("efloat"))).isEqualByComparingTo("900.00");
        assertThat(money(b.get("cash"))).isEqualByComparingTo("0.00");
        assertThat(money(b.get("commissionPayable"))).isEqualByComparingTo(money(tx.get("commission")));
    }

    @Test
    void customerCashDeposit_efloatDecreasesAndCashIncreases() {
        TestAgent agent = createAgent("HRE-METRO", "1000", "500");
        JsonNode tx = postIdempotent(agent.token(), "/api/v1/agent/transactions/deposit",
                customerTx("100", uniquePhone(), "Deposit Customer"), 201);

        assertBalancedJournal(tx);
        JsonNode b = balances(agent);
        assertThat(money(b.get("efloat"))).isEqualByComparingTo("900.00");
        assertThat(money(b.get("cash"))).isEqualByComparingTo("600.00");
        assertThat(money(tx.get("balancesAfter").get("efloat"))).isEqualByComparingTo("900.00");
    }

    @Test
    void customerCashWithdrawal_efloatIncreasesAndCashDecreases() {
        TestAgent agent = createAgent("HRE-METRO", "1000", "500");
        String phone = uniquePhone();
        postIdempotent(agent.token(), "/api/v1/agent/transactions/deposit", customerTx("300", phone, "Withdrawal Customer"), 201);

        JsonNode tx = postIdempotent(agent.token(), "/api/v1/agent/transactions/withdrawal", customerTx("100", phone, null), 201);
        assertBalancedJournal(tx);
        assertThat(money(tx.get("fee"))).isPositive();
        JsonNode b = balances(agent);
        assertThat(money(b.get("efloat"))).isEqualByComparingTo("800.00");
        assertThat(money(b.get("cash"))).isEqualByComparingTo("700.00");
    }

    @Test
    void withdrawalRejectedWhenAgentHasInsufficientCash_noBalancesChange() {
        TestAgent agent = createAgent("HRE-METRO", "1000", "50");
        TestAgent depositAgent = createAgent("HRE-METRO", "1000", "0");
        String phone = uniquePhone();
        postIdempotent(depositAgent.token(), "/api/v1/agent/transactions/deposit", customerTx("300", phone, "Cash Short Customer"), 201);

        JsonNode error = postIdempotent(agent.token(), "/api/v1/agent/transactions/withdrawal", customerTx("200", phone, null), 422);
        assertThat(error.get("errorCode").asText()).isEqualTo("INSUFFICIENT_CASH");
        JsonNode b = balances(agent);
        assertThat(money(b.get("efloat"))).isEqualByComparingTo("1000.00");
        assertThat(money(b.get("cash"))).isEqualByComparingTo("50.00");
    }

    @Test
    void agentToAgentExchange_bothBalancesUpdateAtomically() {
        TestAgent requester = createAgent("HRE-METRO", "1000", "1000");
        TestAgent counterparty = createAgent("HRE-METRO", "1000", "1000");

        JsonNode proposed = postIdempotent(requester.token(), "/api/v1/agent/exchanges", Map.of(
                "counterpartyAgentId", counterparty.id(), "direction", "EFLOAT_FOR_CASH", "amount", new BigDecimal("300.00"),
                "note", "Need cash"), 201);
        assertThat(proposed.get("status").asText()).isEqualTo("PENDING");
        assertThat(money(balances(requester).get("efloat"))).isEqualByComparingTo("1000.00");

        JsonNode accepted = post(counterparty.token(), "/api/v1/agent/exchanges/" + proposed.get("id").asText() + "/accept",
                Map.of("note", "ok"), 200);
        assertThat(accepted.get("status").asText()).isEqualTo("COMPLETED");

        JsonNode r = balances(requester);
        JsonNode c = balances(counterparty);
        assertThat(money(r.get("efloat"))).isEqualByComparingTo("700.00");
        assertThat(money(r.get("cash"))).isEqualByComparingTo("1300.00");
        assertThat(money(c.get("efloat"))).isEqualByComparingTo("1300.00");
        assertThat(money(c.get("cash"))).isEqualByComparingTo("700.00");

        JsonNode tx = get(requester.token(), "/api/v1/agent/transactions/" + accepted.get("transactionId").asText());
        assertBalancedJournal(tx);
        assertThat(tx.get("ledgerEntries").size()).isEqualTo(4);
    }

    @Test
    void cashForEfloatExchange_counterpartySuppliesEfloatAndReceivesCash() {
        TestAgent requester = createAgent("HRE-METRO", "100", "500");
        TestAgent counterparty = createAgent("HRE-METRO", "800", "100");

        JsonNode proposed = postIdempotent(requester.token(), "/api/v1/agent/exchanges", Map.of(
                "counterpartyAgentId", counterparty.id(), "direction", "CASH_FOR_EFLOAT",
                "amount", new BigDecimal("200.00"), "note", "Need e-float"), 201);
        assertThat(proposed.get("status").asText()).isEqualTo("PENDING");

        JsonNode accepted = post(counterparty.token(), "/api/v1/agent/exchanges/" + proposed.get("id").asText() + "/accept",
                Map.of("note", "providing float"), 200);
        assertThat(accepted.get("status").asText()).isEqualTo("COMPLETED");

        JsonNode r = balances(requester);
        JsonNode c = balances(counterparty);
        assertThat(money(r.get("efloat"))).isEqualByComparingTo("300.00");
        assertThat(money(r.get("cash"))).isEqualByComparingTo("300.00");
        assertThat(money(c.get("efloat"))).isEqualByComparingTo("600.00");
        assertThat(money(c.get("cash"))).isEqualByComparingTo("300.00");

        JsonNode tx = get(requester.token(), "/api/v1/agent/transactions/" + accepted.get("transactionId").asText());
        assertBalancedJournal(tx);
        assertThat(tx.get("ledgerEntries").size()).isEqualTo(4);

        TestAgent shortCp = createAgent("HRE-METRO", "50", "500");
        TestAgent needy = createAgent("HRE-METRO", "0", "300");
        JsonNode oversize = postIdempotent(needy.token(), "/api/v1/agent/exchanges", Map.of(
                "counterpartyAgentId", shortCp.id(), "direction", "CASH_FOR_EFLOAT",
                "amount", new BigDecimal("200.00"), "note", "Counterparty cannot cover"), 201);
        JsonNode rejected = expect(call(HttpMethod.POST,
                "/api/v1/agent/exchanges/" + oversize.get("id").asText() + "/accept",
                shortCp.token(), Map.of("note", "try"), newKey()), 422);
        assertThat(rejected.get("errorCode").asText()).isEqualTo("INSUFFICIENT_EFLOAT");
        assertThat(money(balances(needy).get("efloat"))).isEqualByComparingTo("0.00");
        assertThat(money(balances(shortCp).get("efloat"))).isEqualByComparingTo("50.00");
    }

    @Test
    void floatAdvance_efloatIncreasesAndPrincipalAndInterestAreRecorded() {
        TestAgent agent = createAgent("HRE-METRO", "300", "300");
        JsonNode advance = postIdempotent(agent.token(), "/api/v1/agent/advances",
                Map.of("amount", new BigDecimal("500.00"), "termDays", 7, "purpose", "Restock"), 201);
        assertThat(advance.get("decisionRationale").get("checks").size()).isGreaterThan(0);
        assertThat(advance.get("creditScoreAtRequest").asInt()).isBetween(0, 1000);

        String finance = login("finance");
        if (advance.get("status").asText().equals("PENDING")) {
            advance = post(finance, "/api/v1/finance/advances/" + advance.get("id").asText() + "/approve",
                    Map.of("note", "Approved in integration test"), 200);
        }
        assertThat(advance.get("status").asText()).isEqualTo("ACTIVE");
        BigDecimal interest = money(advance.get("interestAmount"));
        assertThat(interest).isPositive();

        JsonNode b = balances(agent);
        assertThat(money(b.get("efloat"))).isEqualByComparingTo("800.00");
        assertThat(money(b.get("advancePrincipalOutstanding"))).isEqualByComparingTo("500.00");
        assertThat(money(b.get("advanceInterestOutstanding"))).isEqualByComparingTo(interest);

        JsonNode detail = get(finance, "/api/v1/finance/advances/" + advance.get("id").asText());
        assertThat(detail.get("advance").get("dueDate").asText()).isNotBlank();

        JsonNode repayment = postIdempotent(agent.token(), "/api/v1/agent/advances/" + advance.get("id").asText() + "/repayments",
                Map.of("amount", money(advance.get("totalOutstanding"))), 201);
        assertThat(repayment.get("advance").get("status").asText()).isEqualTo("REPAID");
        JsonNode after = balances(agent);
        assertThat(money(after.get("advancePrincipalOutstanding"))).isEqualByComparingTo("0.00");
        assertThat(money(after.get("advanceInterestOutstanding"))).isEqualByComparingTo("0.00");
        assertThat(money(after.get("efloat"))).isEqualByComparingTo(new BigDecimal("300.00").subtract(interest));
    }

    @Test
    void areaManagerReplenishment_agentFloatIncreasesAndAreaPoolDecreases() {
        String admin = login("admin");
        UUID area = areaId(admin, "HRE-METRO");
        BigDecimal poolBefore = money(get(admin, "/api/v1/areas/" + area).get("efloatPoolBalance"));
        BigDecimal cashPoolBefore = money(get(admin, "/api/v1/areas/" + area).get("cashPoolBalance"));

        TestAgent agent = createAgent("HRE-METRO", "750", "250");

        JsonNode areaAfter = get(admin, "/api/v1/areas/" + area);
        assertThat(money(areaAfter.get("efloatPoolBalance"))).isEqualByComparingTo(poolBefore.subtract(new BigDecimal("750")));
        assertThat(money(areaAfter.get("cashPoolBalance"))).isEqualByComparingTo(cashPoolBefore.subtract(new BigDecimal("250")));
        JsonNode b = balances(agent);
        assertThat(money(b.get("efloat"))).isEqualByComparingTo("750.00");
        assertThat(money(b.get("cash"))).isEqualByComparingTo("250.00");

        JsonNode replenishments = get(login("manager"), "/api/v1/area-manager/replenishments?agentId=" + agent.id());
        assertThat(replenishments.get("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    void ledgerRemainsBalancedAcrossAllActivity() {
        JsonNode tb = get(login("finance"), "/api/v1/finance/reports/trial-balance");
        assertThat(tb.get("balanced").asBoolean()).isTrue();
        assertThat(money(tb.get("journalDebits"))).isEqualByComparingTo(money(tb.get("journalCredits")));
    }

    static void assertBalancedJournal(JsonNode tx) {
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (JsonNode e : tx.get("ledgerEntries")) {
            if (e.get("side").asText().equals("DEBIT")) {
                debits = debits.add(e.get("amount").decimalValue());
            } else {
                credits = credits.add(e.get("amount").decimalValue());
            }
        }
        assertThat(tx.get("ledgerEntries").size()).isGreaterThanOrEqualTo(2);
        assertThat(debits).isEqualByComparingTo(credits);
    }

    static String uniquePhone() {
        return "+26371" + String.format("%07d", (int) (Math.random() * 9_999_999));
    }
}
