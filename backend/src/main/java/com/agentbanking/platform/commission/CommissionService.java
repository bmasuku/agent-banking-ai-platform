package com.agentbanking.platform.commission;

import com.agentbanking.platform.admin.SettingKey;
import com.agentbanking.platform.admin.SystemSettingService;
import com.agentbanking.platform.advance.AdvanceRecoveryService;
import com.agentbanking.platform.advance.RepaymentSource;
import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.agent.AgentRepository;
import com.agentbanking.platform.audit.AuditService;
import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.ReferenceGenerator;
import com.agentbanking.platform.common.RiskLevel;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.config.AppProperties;
import com.agentbanking.platform.ledger.BalanceSnapshot;
import com.agentbanking.platform.ledger.BalanceType;
import com.agentbanking.platform.ledger.JournalBuilder;
import com.agentbanking.platform.ledger.LedgerAccount;
import com.agentbanking.platform.ledger.LedgerAccountService;
import com.agentbanking.platform.ledger.LedgerService;
import com.agentbanking.platform.notification.NotificationService;
import com.agentbanking.platform.notification.NotificationType;
import com.agentbanking.platform.transaction.AgentTransactionGuard;
import com.agentbanking.platform.transaction.FinancialTransaction;
import com.agentbanking.platform.transaction.TariffCalculator.TariffQuote;
import com.agentbanking.platform.transaction.TransactionType;
import com.agentbanking.platform.websocket.RealtimeEvent;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Commission accrual and disbursement. Accrual happens inside each customer transaction; disbursement
 * pays the whole COMMISSION_PAYABLE balance, first recovering outstanding float advance interest and
 * principal up to the configured percentage, in the configured order, and never beyond available funds.
 */
@Service
@RequiredArgsConstructor
public class CommissionService {

    private final CommissionRepository commissionRepository;
    private final CommissionDisbursementRepository disbursementRepository;
    private final AgentRepository agentRepository;
    private final AgentTransactionGuard guard;
    private final LedgerService ledgerService;
    private final LedgerAccountService accounts;
    private final AdvanceRecoveryService recoveryService;
    private final SystemSettingService settings;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final AppProperties properties;

    /** Records the commission earned on a completed customer transaction (ledger leg posted by the caller). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void accrue(FinancialTransaction transaction, Agent agent, TariffQuote quote) {
        if (!Money.isPositive(quote.commission())) {
            return;
        }
        Commission c = new Commission();
        c.setAgent(agent);
        c.setTransaction(transaction);
        c.setTransactionType(transaction.getType());
        c.setBaseAmount(quote.amount());
        c.setRatePercent(quote.commissionPercent());
        c.setFlatAmount(Money.of(quote.commissionFlat()));
        c.setAmount(quote.commission());
        c.setAccruedAt(transaction.getCreatedAt());
        commissionRepository.save(c);
    }

    @Transactional(readOnly = true)
    public DisbursementPreview preview(UUID agentId) {
        Agent agent = requireAgent(agentId);
        return buildPreview(agent, accounts.agentBalances(agentId));
    }

    @Transactional(readOnly = true)
    public CommissionSummary summary(UUID agentId) {
        Agent agent = requireAgent(agentId);
        BalanceSnapshot balances = accounts.agentBalances(agentId);
        DisbursementResponse last = disbursementRepository.findTopByAgent_IdOrderByCreatedAtDesc(agentId)
                .map(d -> DisbursementResponse.from(d, false)).orElse(null);
        return new CommissionSummary(agentId, agent.getAgentCode(), balances.commissionPayable(),
                commissionRepository.countByAgent_IdAndStatus(agentId, CommissionStatus.ACCRUED),
                Money.of(disbursementRepository.lifetimeGross(agentId)), Money.of(disbursementRepository.lifetimeNet(agentId)),
                last, buildPreview(agent, balances));
    }

    /**
     * Disburses all accrued commission for one agent. Idempotent on {@code idempotencyKey}.
     */
    @Transactional
    public DisbursementResponse disburse(UUID agentId, String idempotencyKey, UUID processedBy) {
        String hash = AgentTransactionGuard.requestHash("COMMISSION_DISBURSEMENT", agentId);
        guard.lockAgent(agentId);
        var replay = guard.findReplay(idempotencyKey, hash, processedBy);
        if (replay.isPresent()) {
            return disbursementRepository.findByTransaction_Id(replay.get().getId())
                    .map(d -> DisbursementResponse.from(d, true))
                    .orElseThrow(() -> BusinessException.conflict("IDEMPOTENCY_KEY_REUSED",
                            "Idempotency-Key was already used for a different operation"));
        }

        Agent agent = requireAgent(agentId);
        BalanceSnapshot balances = accounts.agentBalances(agentId);
        DisbursementPreview preview = buildPreview(agent, balances);
        if (!preview.eligible()) {
            throw BusinessException.unprocessable("COMMISSION_BELOW_MINIMUM", preview.reason(),
                    Map.of("commissionPayable", preview.grossCommission(), "minimumPayout", preview.minimumPayout()));
        }

        LedgerAccount payable = accounts.agent(agentId, BalanceType.COMMISSION_PAYABLE);
        LedgerAccount interest = accounts.agent(agentId, BalanceType.ADVANCE_INTEREST);
        LedgerAccount principal = accounts.agent(agentId, BalanceType.ADVANCE_PRINCIPAL);
        LedgerAccount efloat = accounts.agent(agentId, BalanceType.EFLOAT);

        FinancialTransaction tx = new FinancialTransaction();
        tx.setReference(ReferenceGenerator.next("COM"));
        tx.setIdempotencyKey(idempotencyKey);
        tx.setRequestHash(hash);
        tx.setType(TransactionType.COMMISSION_DISBURSEMENT);
        tx.setAgent(agent);
        tx.setAreaId(agent.getArea().getId());
        tx.setAmount(preview.grossCommission());
        tx.setCurrency(properties.getCurrency());
        tx.setDescription(String.format("Commission disbursement: gross %s, interest recovered %s, principal recovered %s, net %s",
                preview.grossCommission(), preview.interestRecovered(), preview.principalRecovered(), preview.netCommission()));
        tx.setInitiatedBy(processedBy);

        ledgerService.post(tx, JournalBuilder.journal()
                .debit(payable, preview.grossCommission(), "Accrued commission released")
                .credit(interest, preview.interestRecovered(), "Advance interest recovered from commission")
                .credit(principal, preview.principalRecovered(), "Advance principal recovered from commission")
                .credit(efloat, preview.netCommission(), "Net commission paid to e-float")
                .legs());

        CommissionDisbursement d = new CommissionDisbursement();
        d.setReference(tx.getReference());
        d.setAgent(agent);
        d.setGrossCommission(preview.grossCommission());
        d.setInterestRecovered(preview.interestRecovered());
        d.setPrincipalRecovered(preview.principalRecovered());
        d.setNetCommission(preview.netCommission());
        d.setOutstandingAdvanceBefore(preview.outstandingAdvanceBefore());
        d.setOutstandingAdvanceAfter(preview.outstandingAdvanceAfter());
        d.setRecoveryOrder(preview.recoveryOrder());
        d.setMaxRecoveryPercent(preview.maxRecoveryPercent());
        d.setTransaction(tx);
        d.setProcessedBy(processedBy);
        disbursementRepository.save(d);

        commissionRepository.markDisbursed(agentId, d);
        if (preview.interestRecovered().signum() > 0 || preview.principalRecovered().signum() > 0) {
            recoveryService.applyAcrossAdvances(agent, preview.interestRecovered(), preview.principalRecovered(), tx,
                    RepaymentSource.COMMISSION_DEDUCTION, d, processedBy);
        }

        notificationService.notifyAgent(agent, NotificationType.COMMISSION_DISBURSED, RiskLevel.LOW, "Commission paid",
                String.format("Gross commission %s %s: %s recovered against your float advance, %s credited to your e-float.",
                        preview.grossCommission(), tx.getCurrency(), preview.interestRecovered().add(preview.principalRecovered()),
                        preview.netCommission()),
                "COMMISSION_DISBURSEMENT", d.getId());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("agentCode", agent.getAgentCode());
        details.put("gross", preview.grossCommission());
        details.put("interestRecovered", preview.interestRecovered());
        details.put("principalRecovered", preview.principalRecovered());
        details.put("net", preview.netCommission());
        details.put("reference", tx.getReference());
        auditService.record("COMMISSION_DISBURSED", "COMMISSION_DISBURSEMENT", d.getId(), details);
        DisbursementResponse response = DisbursementResponse.from(d, false);
        events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.agent(agentId), "COMMISSION_DISBURSED", response));
        events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.FINANCE, "COMMISSION_DISBURSED", response));
        return response;
    }

    @Transactional(readOnly = true)
    public List<UUID> agentsEligibleForPayout() {
        BigDecimal minimum = settings.getDecimal(SettingKey.COMMISSION_MIN_PAYOUT);
        List<UUID> ids = new ArrayList<>();
        for (Agent agent : agentRepository.findAllByOrderByAgentCode()) {
            if (accounts.agentBalances(agent.getId()).commissionPayable().compareTo(minimum) >= 0) {
                ids.add(agent.getId());
            }
        }
        return ids;
    }

    @Transactional(readOnly = true)
    public PageResponse<CommissionResponse> listCommissions(UUID agentId, CommissionStatus status, Instant from, Instant to,
                                                            Pageable pageable) {
        Specification<Commission> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (agentId != null) {
                p.add(cb.equal(root.get("agent").get("id"), agentId));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("accruedAt"), from));
            }
            if (to != null) {
                p.add(cb.lessThan(root.get("accruedAt"), to));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(commissionRepository.findAll(spec, pageable), CommissionResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<DisbursementResponse> listDisbursements(UUID agentId, Instant from, Instant to, Pageable pageable) {
        Specification<CommissionDisbursement> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (agentId != null) {
                p.add(cb.equal(root.get("agent").get("id"), agentId));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                p.add(cb.lessThan(root.get("createdAt"), to));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(disbursementRepository.findAll(spec, pageable), d -> DisbursementResponse.from(d, false));
    }

    private DisbursementPreview buildPreview(Agent agent, BalanceSnapshot balances) {
        BigDecimal gross = balances.commissionPayable();
        BigDecimal minimum = settings.getDecimal(SettingKey.COMMISSION_MIN_PAYOUT);
        boolean recoveryEnabled = settings.getBoolean(SettingKey.COMMISSION_RECOVERY_ENABLED);
        BigDecimal maxPercent = recoveryEnabled ? settings.getDecimal(SettingKey.COMMISSION_RECOVERY_MAX_PERCENT) : BigDecimal.ZERO;
        RecoveryOrder order = settings.getRecoveryOrder(SettingKey.COMMISSION_RECOVERY_ORDER);
        RecoveryAllocator.Allocation a = RecoveryAllocator.allocate(gross, maxPercent,
                balances.advanceInterestOutstanding(), balances.advancePrincipalOutstanding(), order);
        boolean eligible = gross.signum() > 0 && gross.compareTo(minimum) >= 0;
        String reason = eligible ? null : gross.signum() == 0 ? "No accrued commission to disburse"
                : "Accrued commission " + gross + " is below the minimum payout of " + minimum;
        return new DisbursementPreview(agent.getId(), agent.getAgentCode(), gross, a.interestRecovered(), a.principalRecovered(),
                a.net(), a.outstandingBefore(), a.outstandingAfter(), order, Money.of(maxPercent), recoveryEnabled, a.cap(),
                minimum, eligible, reason);
    }

    private Agent requireAgent(UUID agentId) {
        return agentRepository.findWithDetailsById(agentId).orElseThrow(() -> BusinessException.notFound("Agent", agentId));
    }

    public record DisbursementPreview(UUID agentId, String agentCode, BigDecimal grossCommission, BigDecimal interestRecovered,
                                      BigDecimal principalRecovered, BigDecimal netCommission, BigDecimal outstandingAdvanceBefore,
                                      BigDecimal outstandingAdvanceAfter, RecoveryOrder recoveryOrder, BigDecimal maxRecoveryPercent,
                                      boolean recoveryEnabled, BigDecimal recoveryCap, BigDecimal minimumPayout,
                                      boolean eligible, String reason) {
    }

    public record CommissionSummary(UUID agentId, String agentCode, BigDecimal accruedPayable, long accruedCount,
                                    BigDecimal lifetimeGrossDisbursed, BigDecimal lifetimeNetDisbursed,
                                    DisbursementResponse lastDisbursement, DisbursementPreview nextDisbursementPreview) {
    }

    public record CommissionResponse(UUID id, UUID agentId, String agentCode, UUID transactionId, String transactionReference,
                                     TransactionType transactionType, BigDecimal baseAmount, BigDecimal ratePercent,
                                     BigDecimal flatAmount, BigDecimal amount, CommissionStatus status, Instant accruedAt) {
        static CommissionResponse from(Commission c) {
            return new CommissionResponse(c.getId(), c.getAgent().getId(), c.getAgent().getAgentCode(), c.getTransaction().getId(),
                    c.getTransaction().getReference(), c.getTransactionType(), Money.of(c.getBaseAmount()), c.getRatePercent(),
                    Money.of(c.getFlatAmount()), Money.of(c.getAmount()), c.getStatus(), c.getAccruedAt());
        }
    }

    public record DisbursementResponse(UUID id, String reference, UUID agentId, String agentCode, String agentName,
                                       BigDecimal grossCommission, BigDecimal interestRecovered, BigDecimal principalRecovered,
                                       BigDecimal netCommission, BigDecimal outstandingAdvanceBefore,
                                       BigDecimal outstandingAdvanceAfter, RecoveryOrder recoveryOrder,
                                       BigDecimal maxRecoveryPercent, UUID transactionId, UUID processedBy, Instant createdAt,
                                       boolean replayed) {
        public static DisbursementResponse from(CommissionDisbursement d, boolean replayed) {
            return new DisbursementResponse(d.getId(), d.getReference(), d.getAgent().getId(), d.getAgent().getAgentCode(),
                    d.getAgent().getUser().getFullName(), Money.of(d.getGrossCommission()), Money.of(d.getInterestRecovered()),
                    Money.of(d.getPrincipalRecovered()), Money.of(d.getNetCommission()), Money.of(d.getOutstandingAdvanceBefore()),
                    Money.of(d.getOutstandingAdvanceAfter()), d.getRecoveryOrder(), d.getMaxRecoveryPercent(),
                    d.getTransaction().getId(), d.getProcessedBy(), d.getCreatedAt(), replayed);
        }
    }
}
