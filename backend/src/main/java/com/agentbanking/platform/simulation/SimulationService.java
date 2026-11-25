package com.agentbanking.platform.simulation;

import com.agentbanking.platform.advance.AdvanceApplicationService;
import com.agentbanking.platform.advance.AdvanceDtos.AdvanceRequest;
import com.agentbanking.platform.advance.FloatAdvanceService;
import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.agent.AgentRepository;
import com.agentbanking.platform.ai.AiServiceClient;
import com.agentbanking.platform.commission.CommissionBatchService;
import com.agentbanking.platform.commission.CommissionBatchService.BatchResult;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.customer.Customer;
import com.agentbanking.platform.customer.CustomerRepository;
import com.agentbanking.platform.exchange.ExchangeDirection;
import com.agentbanking.platform.exchange.FloatExchangeService;
import com.agentbanking.platform.exchange.FloatExchangeService.ExchangeResponse;
import com.agentbanking.platform.exchange.FloatExchangeService.ProposeRequest;
import com.agentbanking.platform.ledger.BalanceSnapshot;
import com.agentbanking.platform.ledger.BalanceType;
import com.agentbanking.platform.ledger.LedgerAccount;
import com.agentbanking.platform.ledger.LedgerAccountRepository;
import com.agentbanking.platform.ledger.LedgerAccountService;
import com.agentbanking.platform.prediction.FloatPredictionService;
import com.agentbanking.platform.prediction.FloatPredictionService.PredictionResponse;
import com.agentbanking.platform.prediction.LiquidityRisk;
import com.agentbanking.platform.transaction.TariffService;
import com.agentbanking.platform.transaction.TransactionDtos.CustomerTransactionRequest;
import com.agentbanking.platform.transaction.TransactionDtos.TransactionResult;
import com.agentbanking.platform.transaction.TransactionFacade;
import com.agentbanking.platform.transaction.TransactionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Generates simulated market activity on demand. Every simulated transaction goes through the same
 * public services as the mobile app (fraud scoring, limits, tariff, ledger posting, commission), so
 * simulations exercise the real pipeline. Transactions are spread across agents to stay realistic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationService {

    private final SimulationRunRepository runRepository;
    private final AgentRepository agentRepository;
    private final CustomerRepository customerRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerAccountService accounts;
    private final TransactionFacade transactionFacade;
    private final TariffService tariffService;
    private final FloatExchangeService exchangeService;
    private final AdvanceApplicationService advanceApplicationService;
    private final FloatAdvanceService floatAdvanceService;
    private final CommissionBatchService commissionBatchService;
    private final FloatPredictionService predictionService;
    private final TransactionTemplate transactionTemplate;

    private static final BigDecimal BULAWAYO_LAT = new BigDecimal("-20.15");
    private static final BigDecimal BULAWAYO_LON = new BigDecimal("28.58");

    public record SimulationRequest(@NotNull SimulationScenario scenario, UUID areaId, UUID agentId,
                                    @Min(1) @Max(200) Integer transactionCount, Boolean useAi, Long randomSeed) {
    }

    public record ScenarioInfo(SimulationScenario scenario, String description) {
    }

    public record SimulationRunResponse(UUID id, SimulationScenario scenario, SimulationRun.Status status, Map<String, Object> parameters,
                                        int transactionsAttempted, int transactionsCompleted, int transactionsBlocked,
                                        int transactionsFailed, Map<String, Object> summary, String errorMessage, UUID startedBy,
                                        Instant startedAt, Instant finishedAt) {
        static SimulationRunResponse from(SimulationRun r) {
            return new SimulationRunResponse(r.getId(), r.getScenario(), r.getStatus(), r.getParameters(), r.getTransactionsAttempted(),
                    r.getTransactionsCompleted(), r.getTransactionsBlocked(), r.getTransactionsFailed(), r.getSummary(),
                    r.getErrorMessage(), r.getStartedBy(), r.getStartedAt(), r.getFinishedAt());
        }
    }

    private static final class Tally {
        int attempted;
        int completed;
        int blocked;
        int failed;
        int flagged;
        BigDecimal volume = BigDecimal.ZERO;
        final Map<String, Integer> failures = new LinkedHashMap<>();
        final Map<String, Map<String, Object>> perAgent = new LinkedHashMap<>();
        final List<String> references = new ArrayList<>();
    }

    public List<ScenarioInfo> scenarios() {
        return Arrays.stream(SimulationScenario.values()).map(s -> new ScenarioInfo(s, s.description())).toList();
    }

    public SimulationRunResponse run(SimulationRequest request, UUID startedBy) {
        List<Agent> agents = targetAgents(request);
        int count = request.transactionCount() == null ? defaultCount(request.scenario()) : request.transactionCount();
        boolean useAi = request.useAi() == null || request.useAi();
        long seed = request.randomSeed() == null ? System.nanoTime() : request.randomSeed();

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("transactionCount", count);
        params.put("useAi", useAi);
        params.put("randomSeed", seed);
        params.put("agents", agents.stream().map(Agent::getAgentCode).toList());
        if (request.areaId() != null) {
            params.put("areaId", request.areaId().toString());
        }
        UUID runId = transactionTemplate.execute(status -> {
            SimulationRun run = new SimulationRun();
            run.setScenario(request.scenario());
            run.setParameters(params);
            run.setStartedBy(startedBy);
            return runRepository.save(run).getId();
        });

        Tally tally = new Tally();
        String error = null;
        try {
            Supplier<Void> body = () -> {
                execute(request.scenario(), agents, count, new Random(seed), runId, startedBy, tally);
                return null;
            };
            if (useAi) {
                body.get();
            } else {
                AiServiceClient.withRulesOnly(body);
            }
        } catch (RuntimeException e) {
            log.error("Simulation {} failed", runId, e);
            error = e.getMessage();
        }
        String finalError = error;
        return transactionTemplate.execute(status -> {
            SimulationRun run = runRepository.findById(runId).orElseThrow();
            run.setTransactionsAttempted(tally.attempted);
            run.setTransactionsCompleted(tally.completed);
            run.setTransactionsBlocked(tally.blocked);
            run.setTransactionsFailed(tally.failed);
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("volume", tally.volume);
            summary.put("flaggedForReview", tally.flagged);
            summary.put("failureReasons", tally.failures);
            summary.put("perAgent", tally.perAgent);
            summary.put("sampleReferences", tally.references.stream().limit(20).toList());
            run.setSummary(summary);
            run.setStatus(finalError == null ? SimulationRun.Status.COMPLETED : SimulationRun.Status.FAILED);
            run.setErrorMessage(finalError);
            run.setFinishedAt(AppClock.now());
            return SimulationRunResponse.from(run);
        });
    }

    public PageResponse<SimulationRunResponse> list(Pageable pageable) {
        return PageResponse.of(runRepository.findAllByOrderByStartedAtDesc(pageable), SimulationRunResponse::from);
    }

    public SimulationRunResponse get(UUID id) {
        return runRepository.findById(id).map(SimulationRunResponse::from)
                .orElseThrow(() -> BusinessException.notFound("Simulation run", id));
    }

    private void execute(SimulationScenario scenario, List<Agent> agents, int count, Random random, UUID runId, UUID startedBy,
                         Tally tally) {
        String keyPrefix = "sim-" + runId.toString().substring(0, 8) + "-";
        switch (scenario) {
            case MORNING_RUSH_WITHDRAWALS -> spread(agents, count, random, keyPrefix, tally, i -> TransactionType.CASH_WITHDRAWAL, 20, 300);
            case MARKET_DAY_DEPOSITS -> spread(agents, count, random, keyPrefix, tally, i -> TransactionType.CASH_DEPOSIT, 20, 400);
            case MIXED_TRADING_HOUR -> mixedTrading(agents, count, random, keyPrefix, tally);
            case FRAUD_STRUCTURING_ATTACK -> structuring(agents.get(0), count, random, keyPrefix, tally);
            case PEER_REBALANCING_CYCLE -> rebalance(agents, keyPrefix, tally);
            case NORMAL_AGENT_TRANSACTIONS -> mixedTrading(agents, count, random, keyPrefix, tally);
            case CUSTOMER_DEPOSITS -> spread(agents, count, random, keyPrefix, tally, i -> TransactionType.CASH_DEPOSIT, 15, 450);
            case CUSTOMER_WITHDRAWALS -> spread(agents, count, random, keyPrefix, tally, i -> TransactionType.CASH_WITHDRAWAL, 25, 350);
            case INSUFFICIENT_FLOAT -> insufficientFloat(agents, count, random, keyPrefix, tally);
            case FLOAT_EXCHANGE_REQUESTS -> floatExchangeRequests(agents, count, random, keyPrefix, tally);
            case SUSPICIOUS_TRANSACTIONS -> suspiciousTransactions(agents, count, random, keyPrefix, tally);
            case LOCATION_MISMATCH_ALERTS -> locationMismatch(agents, count, random, keyPrefix, tally);
            case LOAN_DEFAULT_PRESSURE -> loanDefaultPressure(agents, count, random, keyPrefix, tally);
            case FLOAT_ADVANCE_REQUESTS -> floatAdvanceRequests(agents, count, random, keyPrefix, tally);
            case ACCUMULATE_COMMISSIONS -> spread(agents, count, random, keyPrefix, tally, i -> TransactionType.CASH_DEPOSIT, 5, 45);
            case COMMISSION_DISBURSEMENT -> commissionDisbursement(keyPrefix, startedBy, tally);
            case ACCELERATED_ACTIVITY -> acceleratedActivity(agents, count, random, keyPrefix, tally);
            case FLOAT_PREDICTIONS -> floatPredictions(agents, count, tally);
            case AREA_MANAGER_ALERTS -> areaManagerAlerts(agents, count, random, keyPrefix, tally);
        }
    }

    private void mixedTrading(List<Agent> agents, int count, Random random, String keyPrefix, Tally tally) {
        spread(agents, count, random, keyPrefix, tally, i -> {
            double r = random.nextDouble();
            return r < 0.45 ? TransactionType.CASH_DEPOSIT : r < 0.85 ? TransactionType.CASH_WITHDRAWAL
                    : TransactionType.AGENT_TRANSFER_TO_CUSTOMER;
        }, 10, 350);
    }

    private void acceleratedActivity(List<Agent> agents, int count, Random random, String keyPrefix, Tally tally) {
        spread(agents, count, random, keyPrefix, tally, i -> {
            double r = random.nextDouble();
            return r < 0.35 ? TransactionType.CASH_DEPOSIT : r < 0.7 ? TransactionType.CASH_WITHDRAWAL
                    : TransactionType.AGENT_TRANSFER_TO_CUSTOMER;
        }, 5, 280);
    }

    private void insufficientFloat(List<Agent> agents, int count, Random random, String keyPrefix, Tally tally) {
        List<Customer> customers = customerRepository.findAll(PageRequest.of(0, 200)).getContent();
        for (int i = 0; i < count; i++) {
            Agent agent = agents.get(i % agents.size());
            BalanceSnapshot balances = accounts.agentBalances(agent.getId());
            BigDecimal overshoot = BigDecimal.valueOf(100 + random.nextInt(5_000));
            BigDecimal amount = balances.efloat().add(overshoot).setScale(2, RoundingMode.UNNECESSARY);
            Customer c = customers.isEmpty() ? null : customers.get(random.nextInt(customers.size()));
            String phone = c == null ? String.format("+26378%07d", random.nextInt(10_000_000)) : c.getPhoneNumber();
            String name = c == null ? "Simulated Customer " + (i + 1) : c.getFullName();
            TransactionType type = random.nextBoolean() ? TransactionType.CASH_DEPOSIT : TransactionType.AGENT_TRANSFER_TO_CUSTOMER;
            transact(agent, type, amount, phone, name, keyPrefix + "if" + i, tally, null, null);
        }
    }

    /** Proposes float exchanges between paired agents; accepts roughly half so the rest remain pending. */
    private void floatExchangeRequests(List<Agent> agents, int count, Random random, String keyPrefix, Tally tally) {
        int pairs = Math.max(1, count / 2);
        for (int n = 0; n < pairs; n++) {
            Agent a = agents.get(n % agents.size());
            Agent b = agents.get((n + 1) % agents.size());
            if (a.getId().equals(b.getId())) {
                continue;
            }
            BalanceSnapshot s = accounts.agentBalances(a.getId());
            BigDecimal amount = s.efloat().divide(BigDecimal.valueOf(8), 0, RoundingMode.DOWN)
                    .max(new BigDecimal("100")).min(new BigDecimal("1500"));
            amount = amount.divide(BigDecimal.valueOf(50), 0, RoundingMode.DOWN).multiply(BigDecimal.valueOf(50))
                    .setScale(2, RoundingMode.UNNECESSARY);
            tally.attempted++;
            if (amount.compareTo(new BigDecimal("50")) < 0) {
                tally.failed++;
                tally.failures.merge("AMOUNT_TOO_SMALL", 1, Integer::sum);
                continue;
            }
            try {
                ExchangeResponse proposed = exchangeService.propose(a.getId(), new ProposeRequest(b.getId(),
                        ExchangeDirection.EFLOAT_FOR_CASH, amount, "Simulated exchange request"), keyPrefix + "fx" + n,
                        a.getUser().getId());
                tally.references.add(proposed.id().toString());
                if (n % 2 == 0) {
                    ExchangeResponse accepted = exchangeService.accept(proposed.id(), b.getId(), "Simulated acceptance",
                            b.getUser().getId());
                    tally.completed++;
                    tally.volume = tally.volume.add(amount);
                    tally.references.add(accepted.transactionReference());
                    agentTally(tally, a.getAgentCode(), "exchangedOut", amount);
                    agentTally(tally, b.getAgentCode(), "exchangedIn", amount);
                } else {
                    agentTally(tally, a.getAgentCode(), "proposed", amount);
                    agentTally(tally, b.getAgentCode(), "pendingInbound", amount);
                }
            } catch (BusinessException e) {
                tally.failed++;
                tally.failures.merge(e.getErrorCode(), 1, Integer::sum);
            }
        }
    }

    private void suspiciousTransactions(Agent focus, int count, Random random, String keyPrefix, Tally tally) {
        List<Customer> customers = customerRepository.findAll(PageRequest.of(0, 50)).getContent();
        for (int i = 0; i < count; i++) {
            BigDecimal amount = BigDecimal.valueOf(800 + random.nextInt(4_200)).setScale(2, RoundingMode.UNNECESSARY);
            Customer c = customers.isEmpty() ? null : customers.get(random.nextInt(customers.size()));
            String phone = c == null ? "+26378999" + String.format("%04d", i) : c.getPhoneNumber();
            String name = c == null ? "Rapid Transfer " + (i + 1) : c.getFullName();
            transact(focus, TransactionType.AGENT_TRANSFER_TO_CUSTOMER, amount, phone, name, keyPrefix + "su" + i, tally, null, null);
        }
    }

    private void suspiciousTransactions(List<Agent> agents, int count, Random random, String keyPrefix, Tally tally) {
        suspiciousTransactions(agents.get(random.nextInt(agents.size())), count, random, keyPrefix, tally);
    }

    private void locationMismatch(List<Agent> agents, int count, Random random, String keyPrefix, Tally tally) {
        List<Customer> customers = customerRepository.findAll(PageRequest.of(0, 100)).getContent();
        for (int i = 0; i < count; i++) {
            Agent agent = agents.get(i % agents.size());
            BigDecimal amount = BigDecimal.valueOf(50 + random.nextInt(450)).setScale(2, RoundingMode.UNNECESSARY);
            Customer c = customers.isEmpty() ? null : customers.get(random.nextInt(customers.size()));
            String phone = c == null ? String.format("+26377%07d", random.nextInt(10_000_000)) : c.getPhoneNumber();
            String name = c == null ? "Geo Customer " + (i + 1) : c.getFullName();
            BigDecimal jitterLat = BULAWAYO_LAT.add(BigDecimal.valueOf(random.nextDouble() * 0.1 - 0.05)).setScale(6, RoundingMode.HALF_UP);
            BigDecimal jitterLon = BULAWAYO_LON.add(BigDecimal.valueOf(random.nextDouble() * 0.1 - 0.05)).setScale(6, RoundingMode.HALF_UP);
            TransactionType type = i % 3 == 0 ? TransactionType.CASH_WITHDRAWAL : TransactionType.CASH_DEPOSIT;
            transact(agent, type, amount, phone, name, keyPrefix + "geo" + i, tally, jitterLat, jitterLon);
        }
    }

    private void loanDefaultPressure(List<Agent> agents, int count, Random random, String keyPrefix, Tally tally) {
        int advances = Math.min(count, agents.size() * 2);
        for (int i = 0; i < advances; i++) {
            Agent agent = agents.get(i % agents.size());
            int amount = 1_500 + random.nextInt(4_500);
            int term = 7 + random.nextInt(14);
            tally.attempted++;
            try {
                advanceApplicationService.apply(agent.getId(),
                        new AdvanceRequest(BigDecimal.valueOf(amount).setScale(2, RoundingMode.UNNECESSARY), term,
                                "Simulation — repayment stress test"),
                        keyPrefix + "adv" + i, agent.getUser().getId());
                tally.completed++;
                agentTally(tally, agent.getAgentCode(), "advanceRequests", BigDecimal.ONE);
            } catch (BusinessException e) {
                tally.failed++;
                tally.failures.merge(e.getErrorCode(), 1, Integer::sum);
            }
        }
        tally.attempted++;
        try {
            int penalised = floatAdvanceService.accruePenalties();
            tally.completed++;
            tally.failures.merge("PENALTIES_ACCRUED", penalised, Integer::sum);
        } catch (BusinessException e) {
            tally.failed++;
            tally.failures.merge(e.getErrorCode(), 1, Integer::sum);
        }
    }

    private void floatAdvanceRequests(List<Agent> agents, int count, Random random, String keyPrefix, Tally tally) {
        for (int i = 0; i < count; i++) {
            Agent agent = agents.get(i % agents.size());
            int amount = 500 + random.nextInt(2_500);
            int term = 14 + random.nextInt(30);
            tally.attempted++;
            try {
                advanceApplicationService.apply(agent.getId(),
                        new AdvanceRequest(BigDecimal.valueOf(amount).setScale(2, RoundingMode.UNNECESSARY), term,
                                "Simulation float advance"),
                        keyPrefix + "fa" + i, agent.getUser().getId());
                tally.completed++;
                agentTally(tally, agent.getAgentCode(), "advanceApplications", BigDecimal.ONE);
            } catch (BusinessException e) {
                tally.failed++;
                tally.failures.merge(e.getErrorCode(), 1, Integer::sum);
            }
        }
    }

    private void commissionDisbursement(String keyPrefix, UUID startedBy, Tally tally) {
        try {
            BatchResult batch = commissionBatchService.disburseAll(keyPrefix + "comm-batch", startedBy);
            tally.attempted += batch.processed() + batch.failures().size();
            tally.completed += batch.processed();
            tally.failed += batch.failures().size();
            for (CommissionBatchService.BatchFailure f : batch.failures()) {
                tally.failures.merge(f.code(), 1, Integer::sum);
            }
            tally.volume = tally.volume.add(batch.totalNet());
            tally.failures.merge("BATCH_REPLAYED", batch.replayed(), Integer::sum);
        } catch (BusinessException e) {
            tally.failed++;
            tally.failures.merge(e.getErrorCode(), 1, Integer::sum);
        }
    }

    private void floatPredictions(List<Agent> agents, int count, Tally tally) {
        int shortages = 0;
        int n = Math.min(count, agents.size());
        for (int i = 0; i < n; i++) {
            Agent agent = agents.get(i % agents.size());
            tally.attempted++;
            try {
                PredictionResponse prediction = predictionService.predict(agent.getId(), 24);
                tally.completed++;
                agentTally(tally, agent.getAgentCode(), "predictions", BigDecimal.ONE);
                if (prediction.riskType() != LiquidityRisk.NONE) {
                    shortages++;
                }
                tally.references.add(agent.getAgentCode() + ":" + prediction.riskType());
            } catch (BusinessException e) {
                tally.failed++;
                tally.failures.merge(e.getErrorCode(), 1, Integer::sum);
            }
        }
        tally.failures.merge("PREDICTIONS_SHORTAGE_RISK", shortages, Integer::sum);
    }

    /** Drains e-float through deposits until balances approach minimum thresholds. */
    private void areaManagerAlerts(List<Agent> agents, int count, Random random, String keyPrefix, Tally tally) {
        List<Customer> customers = customerRepository.findAll(PageRequest.of(0, 200)).getContent();
        for (int i = 0; i < count; i++) {
            Agent agent = agents.get(i % agents.size());
            BalanceSnapshot balances = accounts.agentBalances(agent.getId());
            BigDecimal headroom = balances.efloat().subtract(agent.getMinEfloatThreshold());
            if (headroom.compareTo(new BigDecimal("20")) <= 0) {
                continue;
            }
            BigDecimal amount = headroom.multiply(BigDecimal.valueOf(0.15 + random.nextDouble() * 0.35))
                    .setScale(0, RoundingMode.DOWN).max(new BigDecimal("30")).min(new BigDecimal("800"));
            Customer c = customers.isEmpty() ? null : customers.get(random.nextInt(customers.size()));
            String phone = c == null ? String.format("+26378%07d", random.nextInt(10_000_000)) : c.getPhoneNumber();
            String name = c == null ? "Deposit Customer " + (i + 1) : c.getFullName();
            transact(agent, TransactionType.CASH_DEPOSIT, amount, phone, name, keyPrefix + "alm" + i, tally, null, null);
        }
    }

    private interface TypeChooser {
        TransactionType choose(int index);
    }

    private void spread(List<Agent> agents, int count, Random random, String keyPrefix, Tally tally, TypeChooser chooser,
                        int minAmount, int maxAmount) {
        List<Customer> customers = customerRepository.findAll(PageRequest.of(0, 200)).getContent();
        for (int i = 0; i < count; i++) {
            Agent agent = agents.get(i % agents.size());
            TransactionType type = chooser.choose(i);
            BigDecimal amount = BigDecimal.valueOf(minAmount + random.nextInt(maxAmount - minAmount + 1)).setScale(2, RoundingMode.UNNECESSARY);
            Optional<Customer> customer;
            if (type == TransactionType.CASH_WITHDRAWAL) {
                customer = customerWithBalance(amount, random);
                if (customer.isEmpty()) {
                    tally.attempted++;
                    tally.failed++;
                    tally.failures.merge("NO_CUSTOMER_WITH_BALANCE", 1, Integer::sum);
                    continue;
                }
            } else {
                customer = customers.isEmpty() ? Optional.empty() : Optional.of(customers.get(random.nextInt(customers.size())));
            }
            String phone = customer.map(Customer::getPhoneNumber).orElse(String.format("+26378%07d", random.nextInt(10_000_000)));
            String name = customer.map(Customer::getFullName).orElse("Simulated Customer " + (i + 1));
            transact(agent, type, amount, phone, name, keyPrefix + i, tally);
        }
    }

    private void structuring(Agent agent, int count, Random random, String keyPrefix, Tally tally) {
        int[] thresholds = {500, 1000};
        List<Customer> customers = customerRepository.findAll(PageRequest.of(0, 50)).getContent();
        for (int i = 0; i < Math.min(count, 12); i++) {
            int threshold = thresholds[i % 4 == 3 ? 1 : 0];
            BigDecimal amount = BigDecimal.valueOf(threshold - 1 - random.nextInt(10)).setScale(2, RoundingMode.UNNECESSARY);
            Customer c = customers.isEmpty() ? null : customers.get(i % Math.min(3, customers.size()));
            String phone = c == null ? "+263780000" + (100 + i % 3) : c.getPhoneNumber();
            String name = c == null ? "Structuring Customer " + (i % 3 + 1) : c.getFullName();
            transact(agent, TransactionType.CASH_DEPOSIT, amount, phone, name, keyPrefix + i, tally);
        }
    }

    /** Pairs the agent with the most surplus e-float against the agent with the least, and exchanges e-float for cash. */
    private void rebalance(List<Agent> agents, String keyPrefix, Tally tally) {
        List<Agent> byEfloat = new ArrayList<>(agents.stream().filter(a -> a.getStatus().canTransact()).toList());
        byEfloat.sort(Comparator.comparing(a -> accounts.agentBalances(a.getId()).efloat()));
        int i = 0;
        int j = byEfloat.size() - 1;
        int n = 0;
        while (i < j) {
            Agent shortEfloat = byEfloat.get(i);
            Agent surplus = byEfloat.get(j);
            BalanceSnapshot s = accounts.agentBalances(surplus.getId());
            BalanceSnapshot t = accounts.agentBalances(shortEfloat.getId());
            BigDecimal amount = s.efloat().subtract(t.efloat()).divide(BigDecimal.valueOf(4), 0, RoundingMode.DOWN)
                    .min(t.cash().setScale(0, RoundingMode.DOWN)).min(new BigDecimal("2000"));
            amount = amount.divide(BigDecimal.valueOf(50), 0, RoundingMode.DOWN).multiply(BigDecimal.valueOf(50)).setScale(2, RoundingMode.UNNECESSARY);
            tally.attempted++;
            if (amount.compareTo(new BigDecimal("50")) < 0) {
                tally.failed++;
                tally.failures.merge("NO_BENEFICIAL_EXCHANGE", 1, Integer::sum);
            } else {
                try {
                    ExchangeResponse proposed = exchangeService.propose(surplus.getId(), new ProposeRequest(shortEfloat.getId(),
                            ExchangeDirection.EFLOAT_FOR_CASH, amount, "Simulated rebalancing"), keyPrefix + "x" + n++,
                            surplus.getUser().getId());
                    ExchangeResponse accepted = exchangeService.accept(proposed.id(), shortEfloat.getId(), "Simulated acceptance",
                            shortEfloat.getUser().getId());
                    tally.completed++;
                    tally.volume = tally.volume.add(amount);
                    tally.references.add(accepted.transactionReference());
                    agentTally(tally, surplus.getAgentCode(), "exchangedOut", amount);
                    agentTally(tally, shortEfloat.getAgentCode(), "exchangedIn", amount);
                } catch (BusinessException e) {
                    tally.failed++;
                    tally.failures.merge(e.getErrorCode(), 1, Integer::sum);
                }
            }
            i++;
            j--;
        }
    }

    private void transact(Agent agent, TransactionType type, BigDecimal amount, String phone, String name, String key, Tally tally) {
        transact(agent, type, amount, phone, name, key, tally, null, null);
    }

    private void transact(Agent agent, TransactionType type, BigDecimal amount, String phone, String name, String key, Tally tally,
                          BigDecimal latitude, BigDecimal longitude) {
        tally.attempted++;
        try {
            TransactionResult result = transactionFacade.execute(agent.getId(), agent.getUser().getId(), type,
                    new CustomerTransactionRequest(amount, phone, name, null, "Simulation", latitude, longitude), key);
            if (result.blocked()) {
                tally.blocked++;
                agentTally(tally, agent.getAgentCode(), "blocked", BigDecimal.ONE);
            } else {
                tally.completed++;
                tally.volume = tally.volume.add(amount);
                agentTally(tally, agent.getAgentCode(), "volume", amount);
            }
            if (result.response().fraud() != null && result.response().fraud().flagged()) {
                tally.flagged++;
            }
            tally.references.add(result.response().reference());
        } catch (BusinessException e) {
            tally.failed++;
            tally.failures.merge(e.getErrorCode(), 1, Integer::sum);
        }
    }

    private void agentTally(Tally tally, String agentCode, String metric, BigDecimal value) {
        Map<String, Object> m = tally.perAgent.computeIfAbsent(agentCode, k -> new LinkedHashMap<>());
        m.merge(metric, value, (a, b) -> ((BigDecimal) a).add((BigDecimal) b));
    }

    private Optional<Customer> customerWithBalance(BigDecimal amount, Random random) {
        BigDecimal needed = amount.add(tariffService.quote(TransactionType.CASH_WITHDRAWAL, amount).fee());
        List<LedgerAccount> wallets = ledgerAccountRepository.findByBalanceTypeWithMinimum(BalanceType.CUSTOMER_WALLET, needed,
                PageRequest.of(0, 50));
        if (wallets.isEmpty()) {
            return Optional.empty();
        }
        return customerRepository.findById(wallets.get(random.nextInt(wallets.size())).getCustomerId());
    }

    private List<Agent> targetAgents(SimulationRequest request) {
        List<Agent> agents;
        if (request.agentId() != null) {
            agents = agentRepository.findWithDetailsById(request.agentId()).map(List::of)
                    .orElseThrow(() -> BusinessException.notFound("Agent", request.agentId()));
        } else if (request.areaId() != null) {
            agents = agentRepository.findByArea_IdOrderByAgentCode(request.areaId());
        } else {
            agents = agentRepository.findAllByOrderByAgentCode();
        }
        agents = agents.stream().filter(a -> a.getStatus().canTransact()).toList();
        if (agents.isEmpty()) {
            throw BusinessException.unprocessable("NO_ACTIVE_AGENTS", "No agents able to transact for this simulation");
        }
        if ((request.scenario() == SimulationScenario.PEER_REBALANCING_CYCLE
                || request.scenario() == SimulationScenario.FLOAT_EXCHANGE_REQUESTS) && agents.size() < 2) {
            throw BusinessException.unprocessable("NOT_ENOUGH_AGENTS", "This scenario needs at least two agents in the target set");
        }
        return agents;
    }

    private int defaultCount(SimulationScenario scenario) {
        return switch (scenario) {
            case FRAUD_STRUCTURING_ATTACK -> 7;
            case PEER_REBALANCING_CYCLE -> 2;
            case COMMISSION_DISBURSEMENT -> 1;
            case FLOAT_PREDICTIONS -> 10;
            case FLOAT_EXCHANGE_REQUESTS -> 8;
            case INSUFFICIENT_FLOAT -> 12;
            case LOAN_DEFAULT_PRESSURE -> 6;
            case FLOAT_ADVANCE_REQUESTS -> 8;
            case ACCUMULATE_COMMISSIONS -> 40;
            case SUSPICIOUS_TRANSACTIONS -> 15;
            case LOCATION_MISMATCH_ALERTS -> 12;
            case AREA_MANAGER_ALERTS -> 25;
            case ACCELERATED_ACTIVITY -> 60;
            case NORMAL_AGENT_TRANSACTIONS, MIXED_TRADING_HOUR, MORNING_RUSH_WITHDRAWALS, MARKET_DAY_DEPOSITS,
                    CUSTOMER_DEPOSITS, CUSTOMER_WITHDRAWALS -> 20;
        };
    }
}
