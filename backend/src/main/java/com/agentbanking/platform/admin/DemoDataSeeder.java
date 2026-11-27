package com.agentbanking.platform.admin;

import com.agentbanking.platform.advance.AdvanceApplicationService;
import com.agentbanking.platform.advance.AdvanceDtos.AdvanceRequest;
import com.agentbanking.platform.advance.AdvanceDtos.AdvanceResponse;
import com.agentbanking.platform.advance.AdvanceDtos.ApproveRequest;
import com.agentbanking.platform.advance.AdvanceStatus;
import com.agentbanking.platform.advance.FloatAdvanceService;
import com.agentbanking.platform.advance.RepaymentSource;
import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.agent.AgentDtos.CreateAgentRequest;
import com.agentbanking.platform.agent.AgentOnboardingService;
import com.agentbanking.platform.ai.AiServiceClient;
import com.agentbanking.platform.ai.FeatureContribution;
import com.agentbanking.platform.area.AreaDtos.AreaResponse;
import com.agentbanking.platform.area.AreaDtos.CreateAreaRequest;
import com.agentbanking.platform.area.AreaService;
import com.agentbanking.platform.commission.CommissionBatchService;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.RiskLevel;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.config.AppProperties;
import com.agentbanking.platform.credit.CreditScoringService;
import com.agentbanking.platform.customer.Customer;
import com.agentbanking.platform.customer.CustomerRepository;
import com.agentbanking.platform.exchange.ExchangeDirection;
import com.agentbanking.platform.exchange.FloatExchangeService;
import com.agentbanking.platform.exchange.FloatExchangeService.ExchangeResponse;
import com.agentbanking.platform.exchange.FloatExchangeService.ProposeRequest;
import com.agentbanking.platform.floatmgmt.FloatManagementService;
import com.agentbanking.platform.floatmgmt.FloatManagementService.FundPoolRequest;
import com.agentbanking.platform.floatmgmt.FloatManagementService.ReplenishRequest;
import com.agentbanking.platform.floatmgmt.FloatManagementService.VaultFundingRequest;
import com.agentbanking.platform.floatmgmt.ReplenishmentType;
import com.agentbanking.platform.fraud.FraudCheck;
import com.agentbanking.platform.fraud.FraudCheckRepository;
import com.agentbanking.platform.ledger.BalanceSnapshot;
import com.agentbanking.platform.ledger.LedgerAccountService;
import com.agentbanking.platform.prediction.FloatPredictionService;
import com.agentbanking.platform.risk.RiskDtos.CreateInvestigationRequest;
import com.agentbanking.platform.risk.RiskDtos.InvestigationResponse;
import com.agentbanking.platform.risk.RiskService;
import com.agentbanking.platform.transaction.TariffService;
import com.agentbanking.platform.transaction.TransactionDtos.CustomerTransactionRequest;
import com.agentbanking.platform.transaction.TransactionDtos.TransactionResult;
import com.agentbanking.platform.transaction.TransactionFacade;
import com.agentbanking.platform.transaction.TransactionType;
import com.agentbanking.platform.user.Role;
import com.agentbanking.platform.user.User;
import com.agentbanking.platform.user.UserRepository;
import com.agentbanking.platform.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Builds the five-agent demonstration world entirely through real services. Opening float is issued
 * from the treasury, historical activity is replayed via {@link AppClock#at}, balances are realigned
 * to published persona targets, and demo credit scores are persisted for the presenter narrative.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataSeeder {

    private final AppProperties properties;
    private final AreaService areaService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final AgentOnboardingService onboardingService;
    private final FloatManagementService floatService;
    private final TransactionFacade transactionFacade;
    private final TariffService tariffService;
    private final CustomerRepository customerRepository;
    private final LedgerAccountService accounts;
    private final AdvanceApplicationService advanceApplicationService;
    private final FloatAdvanceService advanceService;
    private final CommissionBatchService commissionBatchService;
    private final FloatExchangeService exchangeService;
    private final FraudCheckRepository fraudCheckRepository;
    private final RiskService riskService;
    private final CreditScoringService creditScoringService;
    private final FloatPredictionService predictionService;

    public record SeedSummary(int users, int agents, long customers, int transactionsCompleted, int transactionsBlocked,
                              int transactionsSkipped, int advances, int exchanges, int investigations, long durationMs) {
    }

    private record AgentPlan(String username, String fullName, String phone, String businessName, String address, double lat,
                             double lon, int minEfloat, int minCash, int dailyLimit, int openingEfloat, int openingCash,
                             int targetEfloat, int targetCash, int demoScore, int txMin, int txMax, double pDeposit,
                             double pWithdrawal, int amountMin, int amountMax) {
    }

    private record CustomerProfile(String fullName, String phone, String nationalId) {
    }

    private record ScheduledEvent(Instant at, String label, Runnable action) {
    }

    private record DemoCreditProfile(int score, String explanation, String recommendation, boolean eligible,
                                     List<FeatureContribution> factors) {
    }

    /**
     * Five demonstration agents in Harare Metropolitan. Usernames stay agent1–agent5 for API/E2E
     * compatibility; display names are the presenter personas (John, Mary, Peter, Sarah, David).
     */
    private static final List<AgentPlan> AGENTS = List.of(
            // AGENT 001 – JOHN: healthy float, excellent credit, low fraud
            new AgentPlan("agent1", "John Mwangi", "+263771000001", "John's First Street Kiosk",
                    "Shop 4, First Street Mall, Harare CBD", -17.829200, 31.052200,
                    400, 300, 20000, 1800, 900, 1000, 500, 850,
                    6, 9, 0.40, 0.40, 15, 200),
            // AGENT 002 – MARY: surplus float provider for peer exchanges
            new AgentPlan("agent2", "Mary Chikafu", "+263771000002", "Mary's Makoni Market Agency",
                    "Stand 112, Makoni Market, Chitungwiza", -18.012700, 31.075600,
                    500, 400, 20000, 2800, 1200, 2000, 800, 760,
                    6, 9, 0.65, 0.20, 20, 200),
            // AGENT 003 – PETER: moderate activity, chronically short on e-float
            new AgentPlan("agent3", "Peter Ndlovu", "+263771000003", "Peter's Highfield Remittances",
                    "Machipisa Shopping Centre, Highfield, Harare", -17.890300, 30.985600,
                    250, 250, 10000, 500, 600, 100, 300, 650,
                    4, 7, 0.50, 0.35, 15, 120),
            // AGENT 004 – SARAH: prior advance default, manual credit assessment
            new AgentPlan("agent4", "Sarah Dube", "+263771000004", "Sarah's Epworth Growth Point Store",
                    "Epworth Growth Point, Harare South", -17.890000, 31.147500,
                    150, 150, 5000, 400, 400, 50, 200, 420,
                    2, 4, 0.40, 0.40, 10, 60),
            // AGENT 005 – DAVID: suspicious history, high fraud risk
            new AgentPlan("agent5", "David Banda", "+263771000005", "David's Mbare Terminal Services",
                    "Mbare Musika Bus Terminus, Harare", -17.863600, 31.035000,
                    300, 250, 12000, 1400, 700, 800, 400, 280,
                    4, 6, 0.45, 0.35, 15, 150));

    private static final Map<String, DemoCreditProfile> DEMO_CREDIT = Map.of(
            "agent1", new DemoCreditProfile(850,
                    "Excellent repayment history, strong float discipline and low fraud risk. Auto-eligible for advances.",
                    "APPROVE", true,
                    List.of(
                            new FeatureContribution("repaymentHistory", "excellent", 0.28, "POSITIVE",
                                    "Prior advances repaid in full and on time"),
                            new FeatureContribution("fraudRisk", "low", 0.18, "POSITIVE", "No flagged fraud checks"),
                            new FeatureContribution("floatStability", "high", 0.14, "POSITIVE",
                                    "Consistent e-float and cash above thresholds"))),
            "agent2", new DemoCreditProfile(760,
                    "Good repayment history and surplus float available to support peer exchanges across the area.",
                    "APPROVE", true,
                    List.of(
                            new FeatureContribution("repaymentHistory", "good", 0.22, "POSITIVE",
                                    "Advances repaid within term"),
                            new FeatureContribution("peerLiquidity", "provider", 0.16, "POSITIVE",
                                    "Frequently supplies e-float to short agents"),
                            new FeatureContribution("volume30d", "solid", 0.12, "POSITIVE",
                                    "Steady deposit-heavy trading at Makoni Market"))),
            "agent3", new DemoCreditProfile(650,
                    "Moderate transaction activity with recurring e-float shortages. Eligible with standard pricing.",
                    "APPROVE_WITH_CONDITIONS", true,
                    List.of(
                            new FeatureContribution("floatShortages", "frequent", -0.18, "NEGATIVE",
                                    "Repeated low e-float episodes"),
                            new FeatureContribution("repaymentHistory", "adequate", 0.10, "POSITIVE",
                                    "No defaults on record"),
                            new FeatureContribution("activityLevel", "moderate", 0.08, "POSITIVE",
                                    "Stable but modest remittance volume"))),
            "agent4", new DemoCreditProfile(420,
                    "Previous float advance default. Requires manual credit assessment before any new advance.",
                    "MANUAL_REVIEW", false,
                    List.of(
                            new FeatureContribution("priorDefault", "true", -0.35, "NEGATIVE",
                                    "Outstanding / defaulted advance on file"),
                            new FeatureContribution("creditScoreBand", "HIGH", -0.12, "NEGATIVE",
                                    "Score in high-risk band requiring officer review"),
                            new FeatureContribution("cashPosition", "thin", -0.08, "NEGATIVE",
                                    "Low cash and e-float working capital"))),
            "agent5", new DemoCreditProfile(280,
                    "Prior suspicious transaction patterns and open fraud investigation. Advance credit blocked.",
                    "REJECTED", false,
                    List.of(
                            new FeatureContribution("fraudHistory", "high", -0.40, "NEGATIVE",
                                    "Structuring-style deposits and flagged checks"),
                            new FeatureContribution("investigation", "open", -0.20, "NEGATIVE",
                                    "Active risk investigation on agent"),
                            new FeatureContribution("creditScoreBand", "CRITICAL", -0.15, "NEGATIVE",
                                    "Score below auto-rejection threshold"))));

    private static final String[] FIRST_NAMES = {"Tatenda", "Rutendo", "Tinashe", "Nyasha", "Chiedza", "Tapiwa", "Vimbai",
            "Kudzai", "Munashe", "Fadzai", "Takudzwa", "Ruvimbo", "Simbarashe", "Tsitsi", "Blessing", "Precious", "Tafara",
            "Rumbidzai", "Farai", "Nokuthula"};
    private static final String[] LAST_NAMES = {"Mukamuri", "Chirwa", "Marufu", "Sithole", "Zvobgo", "Nyathi", "Mapfumo",
            "Gumbo", "Shumba", "Moyo"};

    private Random random;
    private int sequence;
    private int completed;
    private int blocked;
    private int skipped;
    private int advances;
    private int exchanges;
    private int investigations;

    public synchronized SeedSummary seed() {
        return AiServiceClient.withRulesOnly(this::doSeed);
    }

    private SeedSummary doSeed() {
        long started = System.currentTimeMillis();
        random = new Random(properties.getDemo().getRandomSeed());
        sequence = 0;
        completed = blocked = skipped = advances = exchanges = investigations = 0;

        LocalDate today = AppClock.today();
        int days = Math.max(4, properties.getDemo().getHistoryDays());
        LocalDate start = today.minusDays(days);
        LocalDate setupDay = start.minusDays(1);
        String password = properties.getDemo().getDefaultPassword();

        AreaResponse harare = AppClock.at(at(setupDay, 7, 0), () -> areaService.create(new CreateAreaRequest("HRE-METRO",
                "Harare Metropolitan", "Harare", new BigDecimal("-17.829200"), new BigDecimal("31.052200"))));
        AreaResponse bulawayo = AppClock.at(at(setupDay, 7, 0), () -> areaService.create(new CreateAreaRequest("BYO-METRO",
                "Bulawayo Metropolitan", "Bulawayo", new BigDecimal("-20.132500"), new BigDecimal("28.626500"))));

        User admin = AppClock.at(at(setupDay, 7, 5), () -> staff(properties.getBootstrap().getAdminUsername(),
                properties.getBootstrap().getAdminEmail(), "Alex Admin", "+263772000001", Role.ADMIN, null, password));
        User finance = AppClock.at(at(setupDay, 7, 6), () -> staff("finance", "finance@agentbanking.local", "Grace Finance",
                "+263772000002", Role.FINANCE_OFFICER, null, password));
        User risk = AppClock.at(at(setupDay, 7, 7), () -> staff("risk", "risk@agentbanking.local", "Daniel Risk",
                "+263772000003", Role.RISK_OFFICER, null, password));
        User manager = AppClock.at(at(setupDay, 7, 8), () -> staff("manager", "manager@agentbanking.local", "Patricia Manager",
                "+263772000004", Role.AREA_MANAGER, harare.id(), password));

        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < AGENTS.size(); i++) {
            AgentPlan p = AGENTS.get(i);
            String code = String.format("AGT-%03d", i + 1);
            Instant onboarded = at(setupDay, 7, 15 + i);
            agents.add(AppClock.at(onboarded, () -> onboardingService.create(new CreateAgentRequest(p.username(),
                    p.username() + "@agentbanking.local", p.fullName(), p.phone(), password, p.businessName(), harare.id(),
                    BigDecimal.valueOf(p.lat()), BigDecimal.valueOf(p.lon()), p.address(), BigDecimal.valueOf(p.minEfloat()),
                    BigDecimal.valueOf(p.minCash()), BigDecimal.valueOf(p.dailyLimit())), code, onboarded)));
        }

        AppClock.at(at(setupDay, 8, 0), () -> {
            floatService.fundCentralVault(new VaultFundingRequest(new BigDecimal("150000.00"), "Opening simulated cash capital"),
                    key(), finance.getId());
            floatService.fundAreaPool(harare.id(), new FundPoolRequest(new BigDecimal("80000.00"), new BigDecimal("40000.00"),
                    "Opening allocation for Harare Metropolitan demo area"), key(), finance.getId());
            floatService.fundAreaPool(bulawayo.id(), new FundPoolRequest(new BigDecimal("20000.00"), new BigDecimal("10000.00"),
                    "Opening allocation for Bulawayo Metropolitan"), key(), finance.getId());
        });
        for (int i = 0; i < agents.size(); i++) {
            Agent agent = agents.get(i);
            AgentPlan p = AGENTS.get(i);
            Instant when = at(setupDay, 9, 10 * i);
            replenish(when, agent, manager, ReplenishmentType.EFLOAT, p.openingEfloat(), "Opening e-float allocation");
            replenish(when.plusSeconds(60), agent, manager, ReplenishmentType.CASH, p.openingCash(), "Opening cash float");
        }

        List<CustomerProfile> customers = customerProfiles();
        Instant now = AppClock.now();
        for (int d = 0; d <= days; d++) {
            LocalDate day = start.plusDays(d);
            List<ScheduledEvent> events = new ArrayList<>();
            events.add(new ScheduledEvent(at(day, 0, 30), "penalty accrual", advanceService::accruePenalties));
            for (int i = 0; i < agents.size(); i++) {
                addCustomerActivity(events, day, agents.get(i), AGENTS.get(i), i, customers);
            }
            addStoryEvents(events, d, days, day, agents, manager, finance, risk, customers);
            events.stream()
                    .filter(e -> e.at().isBefore(now.minus(Duration.ofMinutes(5))))
                    .sorted(Comparator.comparing(ScheduledEvent::at))
                    .forEach(this::run);
        }

        // Live pending exchange: Peter (short e-float) asks Mary (surplus) for float
        Agent peter = agents.get(2);
        Agent mary = agents.get(1);
        run(new ScheduledEvent(now.minus(Duration.ofMinutes(90)), "pending float exchange Peter→Mary", () -> {
            ExchangeResponse ex = exchangeService.propose(peter.getId(), new ProposeRequest(mary.getId(),
                    ExchangeDirection.CASH_FOR_EFLOAT, new BigDecimal("200.00"),
                    "Running low on e-float ahead of remittance payouts; have spare cash"), key(), peter.getUser().getId());
            exchanges++;
            log.debug("Pending exchange {}", ex.reference());
        }));

        // Risk demo: habitual small-ticket customer at John's kiosk (daytime), then a sudden large
        // off-hours withdrawal far from the registered outlet — stays PENDING_REVIEW for risk.
        Agent john = agents.get(0);
        Instant habitStart = at(today.minusDays(1), 10, 0);
        Instant suddenFlagAt = at(today, 2, 45);
        if (habitStart.isBefore(now.minus(Duration.ofMinutes(5)))
                && suddenFlagAt.isBefore(now.minus(Duration.ofMinutes(5)))) {
            run(new ScheduledEvent(habitStart, "sudden large withdrawal fraud flag (John)",
                    () -> seedSuddenLargeWithdrawalFlag(john, manager, customers.get(0), today)));
        }

        alignBalancesToTargets(agents, manager, now.minus(Duration.ofMinutes(30)));

        for (int i = 0; i < agents.size(); i++) {
            Agent agent = agents.get(i);
            AgentPlan plan = AGENTS.get(i);
            DemoCreditProfile profile = DEMO_CREDIT.get(plan.username());
            run(new ScheduledEvent(now, "demo credit " + plan.username(), () ->
                    creditScoringService.applyDemoProfile(agent.getId(), profile.score(), profile.explanation(),
                            profile.factors(), profile.recommendation(), profile.eligible())));
            run(new ScheduledEvent(now, "prediction " + plan.username(), () -> predictionService.predict(agent.getId(), 24)));
        }

        SeedSummary summary = new SeedSummary(4 + agents.size(), agents.size(), customerRepository.count(), completed, blocked,
                skipped, advances, exchanges, investigations, System.currentTimeMillis() - started);
        log.info("Demo data seeded: {}", summary);
        return summary;
    }

    private void addCustomerActivity(List<ScheduledEvent> events, LocalDate day, Agent agent, AgentPlan plan, int agentIndex,
                                     List<CustomerProfile> customers) {
        int count = plan.txMin() + random.nextInt(plan.txMax() - plan.txMin() + 1);
        if (day.getDayOfWeek().getValue() >= 6) {
            count = Math.max(2, count * 2 / 3);
        }
        List<Integer> minutes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            minutes.add(7 * 60 + random.nextInt(12 * 60 + 30));
        }
        minutes.sort(Integer::compare);
        for (int minute : minutes) {
            double roll = random.nextDouble();
            TransactionType type = roll < plan.pDeposit() ? TransactionType.CASH_DEPOSIT
                    : roll < plan.pDeposit() + plan.pWithdrawal() ? TransactionType.CASH_WITHDRAWAL
                    : TransactionType.AGENT_TRANSFER_TO_CUSTOMER;
            BigDecimal amount = randomAmount(plan.amountMin(), plan.amountMax());
            CustomerProfile customer = pickCustomer(customers, agentIndex);
            Instant when = at(day, minute / 60, minute % 60).plusSeconds(random.nextInt(60));
            events.add(new ScheduledEvent(when, agent.getAgentCode() + " " + type,
                    () -> customerTransaction(agent, type, amount, customer, null, null)));
        }
    }

    /** Persona story beats for repayment quality, float stress, default and fraud. */
    private void addStoryEvents(List<ScheduledEvent> events, int d, int days, LocalDate day, List<Agent> agents, User manager,
                                User finance, User risk, List<CustomerProfile> customers) {
        Agent john = agents.get(0);
        Agent mary = agents.get(1);
        Agent peter = agents.get(2);
        Agent sarah = agents.get(3);
        Agent david = agents.get(4);

        // John: advance early, replenish to repay, full repayment (excellent history)
        if (d == 1) {
            events.add(new ScheduledEvent(at(day, 10, 0), "John advance", () ->
                    requestAdvance(john, 600, 7, "Month-end liquidity buffer", finance,
                            "Prime agent; excellent history; approved within policy")));
        }
        if (d == Math.min(5, Math.max(2, days - 2))) {
            events.add(new ScheduledEvent(at(day, 15, 0), "John e-float for repayment", () ->
                    replenish(AppClock.now(), john, manager, ReplenishmentType.EFLOAT, 700,
                            "Buffer so John can settle advance in full")));
            events.add(new ScheduledEvent(at(day, 16, 30), "John full repayment", () -> repayOutstanding(john, null)));
        }

        // Mary: smaller advance repaid; supplies e-float to Peter
        if (d == days - 5 && days >= 7) {
            events.add(new ScheduledEvent(at(day, 10, 15), "Mary advance", () ->
                    requestAdvance(mary, 500, 14, "Short working-capital top-up", finance,
                            "Approved; strong peer-liquidity provider")));
        }
        if (d == days - 2 && days >= 7) {
            events.add(new ScheduledEvent(at(day, 14, 0), "Mary e-float for repayment", () ->
                    replenish(AppClock.now(), mary, manager, ReplenishmentType.EFLOAT, 600,
                            "Buffer so Mary can settle advance")));
            events.add(new ScheduledEvent(at(day, 15, 0), "Mary repayment", () -> repayOutstanding(mary, null)));
        }
        if (d == days - 8 || (days < 10 && d == days / 2)) {
            events.add(new ScheduledEvent(at(day, 14, 0), "Peter←Mary float exchange", () ->
                    completedExchange(peter, mary, ExchangeDirection.CASH_FOR_EFLOAT, new BigDecimal("150.00"),
                            "Peer rebalance: Mary supplies e-float to Peter")));
        }

        // Sarah: advance that will default — tiny partial repayment only
        if (d == 2) {
            events.add(new ScheduledEvent(at(day, 11, 0), "Sarah advance (will default)", () ->
                    requestAdvance(sarah, 900, 7, "Restock e-float ahead of pay day", finance,
                            "Approved above model comfort; close monitoring required")));
        }
        if (d == 4) {
            events.add(new ScheduledEvent(at(day, 15, 0), "Sarah partial repayment only", () ->
                    repayOutstanding(sarah, new BigDecimal("50.00"))));
        }

        // David: structuring burst early today (so risk anomalies stay in the recent window) + investigation
        if (d == days) {
            addStructuringBurst(events, day, david, manager, customers);
            events.add(new ScheduledEvent(at(day, 4, 30), "David fraud investigation", () -> openInvestigation(david, risk)));
        }

        if (d == days - 7 || (days < 9 && d == days - 2)) {
            events.add(new ScheduledEvent(at(day, 16, 0), "commission batch", () ->
                    commissionBatchService.disburseAll("seed-commission-" + day, finance.getId())));
        }
    }

    /** David: deposits just below reporting thresholds, split across customers, in the early hours. */
    private void addStructuringBurst(List<ScheduledEvent> events, LocalDate day, Agent agent, User manager,
                                     List<CustomerProfile> customers) {
        events.add(new ScheduledEvent(at(day, 1, 55), "David e-float for structuring demo", () ->
                replenish(AppClock.now(), agent, manager, ReplenishmentType.EFLOAT, 6000,
                        "Demo e-float so structuring deposits can complete and flag")));
        int[] amounts = {495, 498, 499, 997, 490, 499, 995};
        int[] customerIdx = {32, 33, 32, 34, 32, 33, 32};
        for (int i = 0; i < amounts.length; i++) {
            BigDecimal amount = BigDecimal.valueOf(amounts[i]).setScale(2, RoundingMode.UNNECESSARY);
            CustomerProfile customer = customers.get(customerIdx[i]);
            events.add(new ScheduledEvent(at(day, 2, 10 + i * 5), "structuring burst",
                    () -> customerTransaction(agent, TransactionType.CASH_DEPOSIT, amount, customer,
                            agent.getLatitude(), agent.getLongitude())));
        }
    }

    /**
     * Builds a clear risk-officer demo case: a customer used to small daytime tickets suddenly
     * withdraws a suspiciously large amount overnight, captured far from the agent's registered
     * location. Expected: REVIEW / PENDING_REVIEW with amount-deviation + off-hours + location.
     */
    private void seedSuddenLargeWithdrawalFlag(Agent agent, User manager, CustomerProfile customer,
                                               LocalDate spikeDay) {
        LocalDate habitDay = AppClock.today();
        Instant start = AppClock.now();
        replenish(start, agent, manager, ReplenishmentType.EFLOAT, 3000,
                "Demo e-float buffer for sudden-amount fraud story");
        replenish(start.plusSeconds(45), agent, manager, ReplenishmentType.CASH, 2000,
                "Demo cash buffer for sudden-amount fraud story");

        // Daytime habit at the registered outlet — normal ticket sizes for John (~$30–$150).
        // Enough wallet credit so the overnight $1,200 withdrawal is not auto-shrunk.
        int[] smallDeposits = {
                40, 55, 35, 60, 45, 50, 70, 30, 65, 45,
                80, 50, 55, 40, 75, 90, 100, 85, 95, 110,
                120, 100, 90, 80, 70
        };
        for (int i = 0; i < smallDeposits.length; i++) {
            BigDecimal amount = BigDecimal.valueOf(smallDeposits[i]).setScale(2, RoundingMode.UNNECESSARY);
            Instant when = at(habitDay, 9, 0).plus(Duration.ofMinutes(i * 12L));
            AppClock.at(when, () -> customerTransaction(agent, TransactionType.CASH_DEPOSIT, amount, customer,
                    agent.getLatitude(), agent.getLongitude()));
        }
        int[] smallWithdrawals = {20, 15, 25, 30, 18};
        for (int i = 0; i < smallWithdrawals.length; i++) {
            BigDecimal amount = BigDecimal.valueOf(smallWithdrawals[i]).setScale(2, RoundingMode.UNNECESSARY);
            Instant when = at(habitDay, 16, 0).plus(Duration.ofMinutes(i * 12L));
            AppClock.at(when, () -> customerTransaction(agent, TransactionType.CASH_WITHDRAWAL, amount, customer,
                    agent.getLatitude(), agent.getLongitude()));
        }

        // Overnight spike at the outlet (~15× typical ticket). Cash-out is always in-person
        // (distance agent↔customer = 0); risk comes from amount deviation + off-hours + rapid reuse.
        Instant spikeAt = at(spikeDay, 2, 45);
        AppClock.at(spikeAt, () -> customerTransaction(agent, TransactionType.CASH_WITHDRAWAL,
                new BigDecimal("1200.00"), customer, agent.getLatitude(), agent.getLongitude()));
    }

    private void customerTransaction(Agent agent, TransactionType requestedType, BigDecimal requestedAmount,
                                     CustomerProfile customer, BigDecimal latitude, BigDecimal longitude) {
        TransactionType type = requestedType;
        BigDecimal amount = requestedAmount;
        BalanceSnapshot balances = accounts.agentBalances(agent.getId());

        if (type == TransactionType.CASH_WITHDRAWAL) {
            Optional<Customer> existing = customerRepository.findByPhoneNumber(customer.phone());
            BigDecimal wallet = existing.map(c -> accounts.customerBalance(c.getId())).orElse(BigDecimal.ZERO);
            BigDecimal affordable = wallet.subtract(new BigDecimal("5.00")).multiply(new BigDecimal("0.8"))
                    .setScale(0, RoundingMode.DOWN);
            if (affordable.compareTo(BigDecimal.TEN) < 0) {
                type = TransactionType.CASH_DEPOSIT;
            } else if (amount.compareTo(affordable) > 0) {
                amount = affordable.setScale(2, RoundingMode.UNNECESSARY);
            }
        }
        if (type == TransactionType.CASH_WITHDRAWAL) {
            BigDecimal needed = amount.add(tariffService.quote(type, amount).fee());
            if (balances.cash().compareTo(amount) < 0
                    || customerRepository.findByPhoneNumber(customer.phone()).map(c -> accounts.customerBalance(c.getId()))
                    .orElse(BigDecimal.ZERO).compareTo(needed) < 0) {
                skipped++;
                return;
            }
        } else if (balances.efloat().compareTo(amount) < 0) {
            skipped++;
            return;
        }

        TransactionResult result = transactionFacade.execute(agent.getId(), agent.getUser().getId(), type,
                new CustomerTransactionRequest(amount, customer.phone(), customer.fullName(), customer.nationalId(),
                        null, latitude, longitude), key());
        if (result.blocked()) {
            blocked++;
        } else {
            completed++;
        }
    }

    private void requestAdvance(Agent agent, int amount, int termDays, String purpose, User finance, String approvalNote) {
        AdvanceResponse response = advanceApplicationService.apply(agent.getId(),
                new AdvanceRequest(BigDecimal.valueOf(amount).setScale(2, RoundingMode.UNNECESSARY), termDays, purpose), key(),
                agent.getUser().getId());
        advances++;
        if (response.status() == AdvanceStatus.PENDING) {
            AppClock.at(AppClock.now().plus(Duration.ofMinutes(45)), () ->
                    advanceService.approve(response.id(), new ApproveRequest(null, null, null, approvalNote), finance.getId()));
        }
    }

    private void repayOutstanding(Agent agent, BigDecimal amount) {
        advanceService.list(agent.getId(), null, null, Pageable.unpaged()).content().stream()
                .filter(a -> a.status().isOutstanding())
                .findFirst()
                .ifPresent(a -> {
                    BigDecimal pay = amount == null ? a.totalOutstanding() : amount.min(a.totalOutstanding());
                    if (accounts.agentBalances(agent.getId()).efloat().compareTo(pay) >= 0) {
                        advanceService.repay(a.id(), pay, key(), agent.getUser().getId(), RepaymentSource.AGENT_EFLOAT,
                                agent.getId());
                    }
                });
    }

    private void completedExchange(Agent requester, Agent counterparty, ExchangeDirection direction, BigDecimal amount,
                                   String note) {
        ExchangeResponse proposed = exchangeService.propose(requester.getId(),
                new ProposeRequest(counterparty.getId(), direction, amount, note), key(), requester.getUser().getId());
        exchanges++;
        AppClock.at(AppClock.now().plus(Duration.ofMinutes(20)), () ->
                exchangeService.accept(proposed.id(), counterparty.getId(), "Happy to help", counterparty.getUser().getId()));
    }

    private void openInvestigation(Agent agent, User risk) {
        List<FraudCheck> flagged = fraudCheckRepository.findTop20ByAgent_IdAndFlaggedTrueOrderByCreatedAtDesc(agent.getId());
        FraudCheck worst = flagged.stream().max(Comparator.comparing(FraudCheck::getScore)).orElse(null);
        InvestigationResponse inv = riskService.create(new CreateInvestigationRequest(agent.getId(),
                worst == null ? null : worst.getId(),
                "Suspected structuring below reporting thresholds",
                "Early-morning burst of cash deposits between 495 and 999, split across a small group of customers, "
                        + "consistent with structuring to avoid reporting thresholds. Review CCTV, customer KYC and agent float source.",
                RiskLevel.HIGH, risk.getId()), risk.getId());
        investigations++;
        AppClock.at(AppClock.now().plus(Duration.ofMinutes(30)), () -> riskService.addNote(inv.id(),
                "Requested customer KYC documents and terminal logs from the area manager.", risk.getId()));
    }

    /**
     * Brings each agent to the published demo targets using real replenishments / recalls so the
     * ledger stays balanced and auditable (no direct balance writes).
     */
    private void alignBalancesToTargets(List<Agent> agents, User manager, Instant when) {
        for (int i = 0; i < agents.size(); i++) {
            Agent agent = agents.get(i);
            AgentPlan plan = AGENTS.get(i);
            AppClock.at(when.plusSeconds(i * 30L), () -> {
                BalanceSnapshot opening = accounts.agentBalances(agent.getId());
                BigDecimal targetE = BigDecimal.valueOf(plan.targetEfloat()).setScale(2, RoundingMode.UNNECESSARY);
                BigDecimal targetC = BigDecimal.valueOf(plan.targetCash()).setScale(2, RoundingMode.UNNECESSARY);
                BigDecimal eGap = targetE.subtract(opening.efloat());

                if (eGap.compareTo(new BigDecimal("0.01")) > 0) {
                    floatService.replenish(agent.getId(), new ReplenishRequest(ReplenishmentType.EFLOAT, eGap,
                            "Demo balance alignment — e-float target", null), key(), manager.getId());
                } else if (eGap.compareTo(new BigDecimal("-0.01")) < 0) {
                    floatService.recall(agent.getId(), new ReplenishRequest(ReplenishmentType.EFLOAT, eGap.abs(),
                            "Demo balance alignment — recall excess e-float to area pool", null), key(), manager.getId());
                }

                BalanceSnapshot afterEfloat = accounts.agentBalances(agent.getId());
                BigDecimal cGap = targetC.subtract(afterEfloat.cash());
                if (cGap.compareTo(new BigDecimal("0.01")) > 0) {
                    floatService.replenish(agent.getId(), new ReplenishRequest(ReplenishmentType.CASH, cGap,
                            "Demo balance alignment — cash target", null), key(), manager.getId());
                } else if (cGap.compareTo(new BigDecimal("-0.01")) < 0) {
                    floatService.recall(agent.getId(), new ReplenishRequest(ReplenishmentType.CASH, cGap.abs(),
                            "Demo balance alignment — recall excess cash to area vault", null), key(), manager.getId());
                }

                BalanceSnapshot finalBal = accounts.agentBalances(agent.getId());
                log.info("Demo align {}: e-float {} (target {}), cash {} (target {})", agent.getAgentCode(),
                        finalBal.efloat(), targetE, finalBal.cash(), targetC);
            });
        }
    }

    private void replenish(Instant when, Agent agent, User manager, ReplenishmentType type, int amount, String reason) {
        AppClock.at(when, () -> floatService.replenish(agent.getId(), new ReplenishRequest(type,
                BigDecimal.valueOf(amount).setScale(2, RoundingMode.UNNECESSARY), reason, null), key(), manager.getId()));
    }

    private void run(ScheduledEvent event) {
        try {
            AppClock.at(event.at(), event.action());
        } catch (BusinessException e) {
            skipped++;
            log.debug("Seed event '{}' at {} skipped: {} {}", event.label(), event.at(), e.getErrorCode(), e.getMessage());
        } catch (RuntimeException e) {
            skipped++;
            log.warn("Seed event '{}' at {} failed: {}", event.label(), event.at(), e.toString());
        }
    }

    private User staff(String username, String email, String fullName, String phone, Role role, UUID areaId, String password) {
        return userRepository.save(userService.buildUser(username, email, fullName, phone, role, password, areaId));
    }

    private List<CustomerProfile> customerProfiles() {
        List<CustomerProfile> list = new ArrayList<>();
        Map<String, Boolean> used = new LinkedHashMap<>();
        Random names = new Random(properties.getDemo().getRandomSeed() + 7);
        for (int i = 0; list.size() < 40; i++) {
            String name = FIRST_NAMES[names.nextInt(FIRST_NAMES.length)] + " " + LAST_NAMES[names.nextInt(LAST_NAMES.length)];
            if (used.putIfAbsent(name, Boolean.TRUE) != null) {
                continue;
            }
            int n = list.size() + 1;
            list.add(new CustomerProfile(name, String.format("+26377%07d", 3100000 + n * 137),
                    String.format("63-%06d%c%02d", 100000 + n * 4111, (char) ('A' + n % 26), 10 + n % 80)));
        }
        return list;
    }

    private CustomerProfile pickCustomer(List<CustomerProfile> customers, int agentIndex) {
        double roll = random.nextDouble();
        if (agentIndex == 2 && roll < 0.5) {
            return customers.get(8 + random.nextInt(8));
        }
        if (roll < 0.85) {
            return customers.get(agentIndex * 8 + random.nextInt(8));
        }
        return customers.get(random.nextInt(customers.size()));
    }

    private BigDecimal randomAmount(int min, int max) {
        int raw = min + random.nextInt(max - min + 1);
        int rounded = raw >= 50 ? (raw / 5) * 5 : raw;
        return BigDecimal.valueOf(Math.max(min, rounded)).setScale(2, RoundingMode.UNNECESSARY);
    }

    private String key() {
        return String.format("seed-%06d", ++sequence);
    }

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(LocalTime.of(hour, minute)).atZone(AppClock.zone()).toInstant();
    }
}
