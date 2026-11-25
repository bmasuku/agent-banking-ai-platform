package com.agentbanking.platform.transaction;

import com.agentbanking.platform.admin.SettingKey;
import com.agentbanking.platform.admin.SystemSettingService;
import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.agent.AgentAccount;
import com.agentbanking.platform.agent.AgentAccountStatus;
import com.agentbanking.platform.agent.AgentRepository;
import com.agentbanking.platform.commission.CommissionService;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.common.ReferenceGenerator;
import com.agentbanking.platform.common.RiskLevel;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.config.AppProperties;
import com.agentbanking.platform.customer.Customer;
import com.agentbanking.platform.customer.CustomerService;
import com.agentbanking.platform.fraud.FraudAssessment;
import com.agentbanking.platform.fraud.FraudDecision;
import com.agentbanking.platform.fraud.FraudDetectionService;
import com.agentbanking.platform.fraud.FraudFeatures;
import com.agentbanking.platform.ledger.BalanceSnapshot;
import com.agentbanking.platform.ledger.BalanceType;
import com.agentbanking.platform.ledger.JournalBuilder;
import com.agentbanking.platform.ledger.LedgerAccount;
import com.agentbanking.platform.ledger.LedgerAccountService;
import com.agentbanking.platform.ledger.LedgerService;
import com.agentbanking.platform.ledger.PostingLeg;
import com.agentbanking.platform.notification.NotificationService;
import com.agentbanking.platform.notification.NotificationType;
import com.agentbanking.platform.transaction.TariffCalculator.TariffQuote;
import com.agentbanking.platform.transaction.TransactionDtos.TransactionResponse;
import com.agentbanking.platform.transaction.TransactionDtos.TransactionResult;
import com.agentbanking.platform.websocket.RealtimeEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Transactional engine for customer transactions at an agent. Serialises per agent by locking the
 * agent account, enforces limits, applies the tariff, posts a balanced journal and accrues commission
 * atomically. Fraud scoring is computed by the caller before the transaction starts.
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final AgentTransactionGuard guard;
    private final AgentRepository agentRepository;
    private final CustomerService customerService;
    private final TariffService tariffService;
    private final LedgerService ledgerService;
    private final LedgerAccountService accounts;
    private final CommissionService commissionService;
    private final FraudDetectionService fraudDetectionService;
    private final FinancialTransactionRepository transactionRepository;
    private final TransactionQueryService queryService;
    private final SystemSettingService settings;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher events;
    private final AppProperties properties;

    public record Command(UUID agentId, UUID initiatorId, TransactionType type, BigDecimal amount, String customerPhone,
                          String customerName, String customerNationalId, String narration,
                          BigDecimal latitude, BigDecimal longitude, String idempotencyKey,
                          String requestHash, FraudFeatures fraudFeatures, FraudAssessment fraudAssessment) {
    }

    @Transactional
    public TransactionResult execute(Command cmd) {
        if (!cmd.type().isCustomerTransaction()) {
            throw new IllegalArgumentException("Not a customer transaction type: " + cmd.type());
        }
        AgentAccount account = guard.lockAgent(cmd.agentId());
        Optional<FinancialTransaction> replay = guard.findReplay(cmd.idempotencyKey(), cmd.requestHash(), cmd.initiatorId());
        if (replay.isPresent()) {
            return new TransactionResult(queryService.toResponse(replay.get(), true), false);
        }

        Agent agent = agentRepository.findWithDetailsById(cmd.agentId())
                .orElseThrow(() -> BusinessException.notFound("Agent", cmd.agentId()));
        if (!agent.getStatus().canTransact() || account.getStatus() != AgentAccountStatus.ACTIVE) {
            throw BusinessException.unprocessable("AGENT_NOT_ACTIVE",
                    "Agent " + agent.getAgentCode() + " cannot transact (status " + agent.getStatus() + ", account " + account.getStatus() + ")");
        }
        BigDecimal amount = Money.requireValidAmount(cmd.amount(), "amount");
        enforceLimits(agent, account, amount);

        Customer customer = cmd.type() == TransactionType.CASH_WITHDRAWAL
                ? customerService.requireActiveByPhone(cmd.customerPhone())
                : customerService.findOrRegister(cmd.customerPhone(), cmd.customerName(), cmd.customerNationalId(), agent);
        TariffQuote quote = tariffService.quote(cmd.type(), amount);

        FinancialTransaction tx = new FinancialTransaction();
        tx.setReference(ReferenceGenerator.next("TXN"));
        tx.setIdempotencyKey(cmd.idempotencyKey());
        tx.setRequestHash(cmd.requestHash());
        tx.setType(cmd.type());
        tx.setAgent(agent);
        tx.setCustomer(customer);
        tx.setAreaId(agent.getArea().getId());
        tx.setAmount(amount);
        tx.setFeeAmount(quote.fee());
        tx.setCommissionAmount(quote.commission());
        tx.setCurrency(properties.getCurrency());
        tx.setDescription(describe(cmd.type(), amount, customer, cmd.narration()));
        tx.setInitiatedBy(cmd.initiatorId());
        tx.setLatitude(cmd.latitude());
        tx.setLongitude(cmd.longitude());

        FraudAssessment assessment = cmd.fraudAssessment();
        if (assessment.decision() == FraudDecision.BLOCK) {
            tx.setStatus(TransactionStatus.BLOCKED);
            tx.setCommissionAmount(Money.ZERO);
            tx.setFeeAmount(Money.ZERO);
            tx.setFailureReason("Blocked by fraud controls: " + String.join(", ", assessment.reasonCodes()));
            transactionRepository.save(tx);
            fraudDetectionService.record(tx, agent, cmd.fraudFeatures(), assessment);
            notificationService.notifyAgent(agent, NotificationType.TRANSACTION_BLOCKED, RiskLevel.HIGH, "Transaction blocked",
                    tx.getReference() + " was blocked by fraud controls and referred to the risk team. No balances changed.",
                    "TRANSACTION", tx.getId());
            TransactionResponse response = queryService.toResponse(tx, false);
            events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.agent(agent.getId()), "TRANSACTION_BLOCKED", response));
            return new TransactionResult(response, true);
        }

        BalanceSnapshot before = accounts.agentBalances(agent.getId());
        ledgerService.post(tx, buildLegs(cmd.type(), agent, customer, quote));
        commissionService.accrue(tx, agent, quote);
        fraudDetectionService.record(tx, agent, cmd.fraudFeatures(), assessment);

        BalanceSnapshot after = accounts.agentBalances(agent.getId());
        checkLiquidity(agent, before, after);
        TransactionResponse response = queryService.toResponse(tx, false);
        publish(agent, response, after);
        return new TransactionResult(response, true);
    }

    private List<PostingLeg> buildLegs(TransactionType type, Agent agent, Customer customer, TariffQuote q) {
        UUID agentId = agent.getId();
        LedgerAccount efloat = accounts.agent(agentId, BalanceType.EFLOAT);
        LedgerAccount wallet = accounts.customerWallet(customer.getId());
        LedgerAccount feeIncome = accounts.system(BalanceType.FEE_INCOME);
        LedgerAccount commissionExpense = accounts.system(BalanceType.COMMISSION_EXPENSE);
        LedgerAccount commissionPayable = accounts.agent(agentId, BalanceType.COMMISSION_PAYABLE);
        JournalBuilder j = JournalBuilder.journal();
        switch (type) {
            case CASH_DEPOSIT -> {
                LedgerAccount cash = accounts.agent(agentId, BalanceType.CASH);
                LedgerAccount cashControl = accounts.system(BalanceType.CASH_CONTROL);
                j.pair(efloat, wallet, q.amount(), "E-money sent to customer wallet")
                        .pair(cash, cashControl, q.amount(), "Cash received from customer");
            }
            case CASH_WITHDRAWAL -> {
                LedgerAccount cash = accounts.agent(agentId, BalanceType.CASH);
                LedgerAccount cashControl = accounts.system(BalanceType.CASH_CONTROL);
                j.pair(wallet, efloat, q.amount(), "E-money received from customer wallet")
                        .pair(cashControl, cash, q.amount(), "Cash paid out to customer");
            }
            case AGENT_TRANSFER_TO_CUSTOMER -> j.pair(efloat, wallet, q.amount(), "E-float transferred to customer wallet");
            default -> throw new IllegalArgumentException("Unsupported type " + type);
        }
        j.pair(wallet, feeIncome, q.fee(), "Customer transaction fee")
                .pair(commissionExpense, commissionPayable, q.commission(), "Agent commission accrued");
        return j.legs();
    }

    private void enforceLimits(Agent agent, AgentAccount account, BigDecimal amount) {
        BigDecimal min = settings.getDecimal(SettingKey.TRANSACTION_MIN_AMOUNT);
        BigDecimal max = settings.getDecimal(SettingKey.TRANSACTION_MAX_AMOUNT);
        if (amount.compareTo(min) < 0 || amount.compareTo(max) > 0) {
            throw BusinessException.unprocessable("AMOUNT_OUT_OF_RANGE", "Amount must be between " + min + " and " + max,
                    Map.of("minimum", min, "maximum", max));
        }
        Instant startOfDay = AppClock.today().atStartOfDay(AppClock.zone()).toInstant();
        BigDecimal usedToday = Money.of(transactionRepository.sumAgentAmountSince(agent.getId(),
                EnumSet.of(TransactionType.CASH_DEPOSIT, TransactionType.CASH_WITHDRAWAL, TransactionType.AGENT_TRANSFER_TO_CUSTOMER),
                startOfDay));
        BigDecimal limit = Money.of(account.getDailyTransactionLimit());
        if (usedToday.add(amount).compareTo(limit) > 0) {
            throw BusinessException.unprocessable("DAILY_LIMIT_EXCEEDED",
                    "Daily transaction limit of " + limit + " would be exceeded (used " + usedToday + ")",
                    Map.of("dailyLimit", limit, "usedToday", usedToday, "remaining", Money.max(Money.ZERO, limit.subtract(usedToday))));
        }
    }

    private void checkLiquidity(Agent agent, BalanceSnapshot before, BalanceSnapshot after) {
        alertIfCrossed(agent, "e-float", before.efloat(), after.efloat(), Money.of(agent.getMinEfloatThreshold()));
        alertIfCrossed(agent, "cash", before.cash(), after.cash(), Money.of(agent.getMinCashThreshold()));
    }

    private void alertIfCrossed(Agent agent, String label, BigDecimal before, BigDecimal after, BigDecimal threshold) {
        if (before.compareTo(threshold) >= 0 && after.compareTo(threshold) < 0) {
            String title = "Low " + label + " (" + agent.getAgentCode() + ")";
            String message = String.format("%s %s balance fell to %s, below the minimum of %s. Request replenishment or a float exchange.",
                    agent.getAgentCode(), label, after, threshold);
            notificationService.notifyAgent(agent, NotificationType.LOW_LIQUIDITY, RiskLevel.HIGH, "Low " + label + " balance",
                    message, "AGENT", agent.getId());
            notificationService.notifyAreaManagers(agent.getArea().getId(), NotificationType.LOW_LIQUIDITY, RiskLevel.HIGH, title,
                    message, "AGENT", agent.getId());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("agentId", agent.getId());
            payload.put("agentCode", agent.getAgentCode());
            payload.put("balanceType", label.equals("cash") ? "CASH" : "EFLOAT");
            payload.put("balance", after);
            payload.put("threshold", threshold);
            events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.area(agent.getArea().getId()), "LOW_LIQUIDITY", payload));
        }
    }

    private void publish(Agent agent, TransactionResponse response, BalanceSnapshot balances) {
        events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.agent(agent.getId()), "TRANSACTION_COMPLETED", response));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentId", agent.getId());
        payload.put("agentCode", agent.getAgentCode());
        payload.put("transactionId", response.id());
        payload.put("type", response.type());
        payload.put("amount", response.amount());
        payload.put("balances", balances);
        events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.area(agent.getArea().getId()), "AGENT_BALANCE_UPDATED", payload));
    }

    private String describe(TransactionType type, BigDecimal amount, Customer customer, String narration) {
        String base = switch (type) {
            case CASH_DEPOSIT -> "Cash deposit of " + amount + " for " + customer.getFullName();
            case CASH_WITHDRAWAL -> "Cash withdrawal of " + amount + " by " + customer.getFullName();
            case AGENT_TRANSFER_TO_CUSTOMER -> "E-float transfer of " + amount + " to " + customer.getFullName();
            default -> type.name();
        };
        String full = narration == null || narration.isBlank() ? base : base + " - " + narration.trim();
        return full.length() > 255 ? full.substring(0, 255) : full;
    }
}
