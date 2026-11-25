package com.agentbanking.platform;

import com.agentbanking.platform.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyAndConcurrencyIntegrationTest extends IntegrationTestBase {

    @Test
    void retriedRequestWithSameKeyIsReplayedWithoutMovingMoneyTwice() {
        TestAgent agent = createAgent("HRE-METRO", "1000", "0");
        String key = newKey();
        Map<String, Object> body = customerTx("150", FinancialScenariosIntegrationTest.uniquePhone(), "Retry Customer");

        ResponseEntity<JsonNode> first = call(HttpMethod.POST, "/api/v1/agent/transactions/transfer", agent.token(), body, key);
        ResponseEntity<JsonNode> second = call(HttpMethod.POST, "/api/v1/agent/transactions/transfer", agent.token(), body, key);

        assertThat(first.getStatusCode().value()).isEqualTo(201);
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        assertThat(second.getHeaders().getFirst("Idempotency-Replayed")).isEqualTo("true");
        assertThat(second.getBody().get("id").asText()).isEqualTo(first.getBody().get("id").asText());
        assertThat(second.getBody().get("replayed").asBoolean()).isTrue();
        assertThat(money(balances(agent).get("efloat"))).isEqualByComparingTo("850.00");
    }

    @Test
    void reusingKeyForDifferentRequestIsRejected() {
        TestAgent agent = createAgent("HRE-METRO", "1000", "0");
        String key = newKey();
        String phone = FinancialScenariosIntegrationTest.uniquePhone();
        expect(call(HttpMethod.POST, "/api/v1/agent/transactions/transfer", agent.token(), customerTx("100", phone, "Key Customer"), key), 201);

        JsonNode error = expect(call(HttpMethod.POST, "/api/v1/agent/transactions/transfer", agent.token(),
                customerTx("200", phone, "Key Customer"), key), 409);
        assertThat(error.get("errorCode").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(money(balances(agent).get("efloat"))).isEqualByComparingTo("900.00");
    }

    @Test
    void missingOrMalformedKeyIsRejected() {
        TestAgent agent = createAgent("HRE-METRO", "100", "0");
        Map<String, Object> body = customerTx("10", FinancialScenariosIntegrationTest.uniquePhone(), "No Key");
        assertThat(call(HttpMethod.POST, "/api/v1/agent/transactions/transfer", agent.token(), body, null).getStatusCode().value())
                .isEqualTo(400);
        JsonNode error = expect(call(HttpMethod.POST, "/api/v1/agent/transactions/transfer", agent.token(), body, "bad key!"), 400);
        assertThat(error.get("errorCode").asText()).isEqualTo("INVALID_IDEMPOTENCY_KEY");
    }

    @Test
    void concurrentDebitsNeverOverdrawEfloat() throws Exception {
        TestAgent agent = createAgent("HRE-METRO", "500", "0");
        int attempts = 10;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            Map<String, Object> body = customerTx("100", FinancialScenariosIntegrationTest.uniquePhone(), "Concurrent " + i);
            Callable<Integer> task = () -> {
                start.await();
                return call(HttpMethod.POST, "/api/v1/agent/transactions/transfer", agent.token(), body, newKey())
                        .getStatusCode().value();
            };
            results.add(pool.submit(task));
        }
        start.countDown();
        int created = 0;
        int rejected = 0;
        for (Future<Integer> f : results) {
            int status = f.get();
            if (status == 201) {
                created++;
            } else if (status == 422) {
                rejected++;
            }
        }
        pool.shutdown();

        assertThat(created).isEqualTo(5);
        assertThat(rejected).isEqualTo(5);
        assertThat(money(balances(agent).get("efloat"))).isEqualByComparingTo("0.00");
    }

    @Test
    void concurrentRetriesWithSameKeyCreateExactlyOneTransaction() throws Exception {
        TestAgent agent = createAgent("HRE-METRO", "1000", "0");
        String key = newKey();
        Map<String, Object> body = customerTx("100", FinancialScenariosIntegrationTest.uniquePhone(), "Same Key Customer");
        int attempts = 6;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<JsonNode>>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return call(HttpMethod.POST, "/api/v1/agent/transactions/transfer", agent.token(), body, key);
            }));
        }
        start.countDown();
        int created = 0;
        Set<String> ids = new HashSet<>();
        for (Future<ResponseEntity<JsonNode>> f : results) {
            ResponseEntity<JsonNode> r = f.get();
            assertThat(r.getStatusCode().value()).isIn(200, 201);
            if (r.getStatusCode().value() == 201) {
                created++;
            }
            ids.add(r.getBody().get("id").asText());
        }
        pool.shutdown();

        assertThat(created).isEqualTo(1);
        assertThat(ids).hasSize(1);
        assertThat(money(balances(agent).get("efloat"))).isEqualByComparingTo("900.00");
    }
}
