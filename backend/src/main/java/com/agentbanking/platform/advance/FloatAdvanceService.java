package com.agentbanking.platform.advance;

import com.agentbanking.platform.admin.SettingKey;
import com.agentbanking.platform.admin.SystemSettingService;
import com.agentbanking.platform.advance.AdvanceDtos.AdvanceDetail;
import com.agentbanking.platform.advance.AdvanceDtos.AdvanceRequest;
import com.agentbanking.platform.advance.AdvanceDtos.AdvanceResponse;
import com.agentbanking.platform.advance.AdvanceDtos.ApproveRequest;
import com.agentbanking.platform.advance.AdvanceDtos.RepaymentResult;
import com.agentbanking.platform.advance.AdvanceDtos.RepaymentView;
import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.agent.AgentRepository;
import com.agentbanking.platform.agent.AgentStatus;
import com.agentbanking.platform.ai.AiGovernanceFeedbackClient;
import com.agentbanking.platform.audit.AuditService;
import com.agentbanking.platform.commission.RecoveryAllocator;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.ReferenceGenerator;
import com.agentbanking.platform.common.RiskLevel;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.config.AppProperties;
import com.agentbanking.platform.credit.CreditScoringService.CreditScoreResponse;
import com.agentbanking.platform.ledger.BalanceType;
import com.agentbanking.platform.ledger.JournalBuilder;
import com.agentbanking.platform.ledger.LedgerAccountService;
import com.agentbanking.platform.ledger.LedgerService;
import com.agentbanking.platform.notification.NotificationService;
import com.agentbanking.platform.notification.NotificationType;
import com.agentbanking.platform.transaction.AgentTransactionGuard;
import com.agentbanking.platform.transaction.FinancialTransaction;
import com.agentbanking.platform.transaction.TransactionType;
import com.agentbanking.platform.user.Role;
import com.agentbanking.platform.websocket.RealtimeEvent;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Float advance lifecycle: request with automatic or manual credit decision, disbursement, repayment,
 * and overdue penalty accrual. Every balance change is posted through {@link LedgerService} under the
 * agent's account lock.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FloatAdvanceService {

    private final FloatAdvanceRepository advanceRepository;
    private final AdvanceRepaymentRepository repaymentRepository;
    private final AdvanceRecoveryService recoveryService;
    private final AgentRepository agentRepository;
    private final AgentTransactionGuard guard;
    private final LedgerService ledgerService;
    private final LedgerAccountService accounts;
    private final SystemSettingService settings;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final AppProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final AiGovernanceFeedbackClient aiGovernanceFeedback;

    @Transactional(readOnly = true)
    public Optional<AdvanceResponse> findReplay(String idempotencyKey, UUID agentId) {
        return advanceRepository.findByIdempotencyKey(idempotencyKey).map(existing -> {
            if (!existing.getAgent().getId().equals(agentId)) {
                throw BusinessException.conflict("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key was already used for a different request");
            }
            return AdvanceResponse.from(advanceRepository.findDetailedById(existing.getId()).orElseThrow(), true);
        });
    }

    /** Creates an advance request and applies the automatic credit policy using a fresh credit evaluation. */
    @Transactional
    public AdvanceResponse submit(UUID agentId, AdvanceRequest request, String idempotencyKey, UUID requestedBy,
                                  CreditScoreResponse credit) {
        guard.lockAgent(agentId);
        Optional<FloatAdvance> existing = advanceRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getAgent().getId().equals(agentId)) {
                throw BusinessException.conflict("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key was already used for a different request");
            }
            return AdvanceResponse.from(existing.get(), true);
        }
        Agent agent = requireAgent(agentId);
        if (!agent.getStatus().canTransact()) {
            throw BusinessException.unprocessable("AGENT_NOT_ACTIVE", "Agent status " + agent.getStatus() + " does not permit float advances");
        }
        BigDecimal amount = Money.requireValidAmount(request.amount(), "amount");
        BigDecimal minimum = settings.getDecimal(SettingKey.ADVANCE_MIN_AMOUNT);
        int maxTerm = settings.getInt(SettingKey.ADVANCE_MAX_TERM_DAYS);
        if (amount.compareTo(minimum) < 0) {
            throw BusinessException.unprocessable("ADVANCE_BELOW_MINIMUM", "Minimum float advance is " + minimum);
        }
        if (request.termDays() > maxTerm) {
            throw BusinessException.unprocessable("ADVANCE_TERM_TOO_LONG", "Maximum advance term is " + maxTerm + " days");
        }
        if (advanceRepository.countByAgent_IdAndStatus(agentId, AdvanceStatus.PENDING) > 0) {
            throw BusinessException.conflict("ADVANCE_ALREADY_PENDING", "An advance request is already awaiting a decision");
        }

        BigDecimal outstandingPrincipal = accounts.agentBalances(agentId).advancePrincipalOutstanding();
        BigDecimal availableLimit = Money.max(Money.ZERO, credit.recommendedLimit().subtract(outstandingPrincipal));
        long overdue = advanceRepository.findByAgentAndStatuses(agentId, EnumSet.of(AdvanceStatus.ACTIVE, AdvanceStatus.OVERDUE))
                .stream().filter(a -> a.getDueDate() != null && a.getDueDate().isBefore(AppClock.today())).count();
        BigDecimal autoMax = settings.getDecimal(SettingKey.ADVANCE_AUTO_APPROVE_MAX_AMOUNT);
        int autoMinScore = settings.getInt(SettingKey.ADVANCE_AUTO_APPROVE_MIN_SCORE);

        List<Map<String, Object>> checks = new ArrayList<>();
        boolean scoreOk = check(checks, "CREDIT_SCORE", credit.score() >= autoMinScore,
                "Credit score " + credit.score() + " vs auto-approval minimum " + autoMinScore);
        boolean amountOk = check(checks, "AUTO_APPROVAL_AMOUNT", amount.compareTo(autoMax) <= 0,
                "Requested " + amount + " vs auto-approval maximum " + autoMax);
        boolean limitOk = check(checks, "CREDIT_LIMIT", amount.compareTo(availableLimit) <= 0,
                "Requested " + amount + " vs available limit " + availableLimit + " (limit " + credit.recommendedLimit()
                        + " less outstanding principal " + outstandingPrincipal + ")");
        boolean noOverdue = check(checks, "NO_OVERDUE_ADVANCES", overdue == 0, overdue + " overdue advance(s)");
        boolean statusOk = check(checks, "AGENT_STATUS", agent.getStatus() == AgentStatus.ACTIVE, "Agent status " + agent.getStatus());
        boolean bandOk = check(checks, "RISK_BAND", credit.riskBand() != RiskLevel.CRITICAL, "Risk band " + credit.riskBand());
        boolean modelOk = check(checks, "MODEL_RECOMMENDATION",
                credit.recommendation() == null || "APPROVED".equals(credit.recommendation()),
                "Model recommendation " + credit.recommendation());

        FloatAdvance advance = new FloatAdvance();
        advance.setReference(ReferenceGenerator.next("ADV"));
        advance.setAgent(agent);
        advance.setRequestedAmount(amount);
        advance.setTermDays(request.termDays());
        advance.setPurpose(request.purpose());
        advance.setDailyInterestRate(credit.recommendedDailyRate());
        advance.setCreditScoreAtRequest(credit.score());
        advance.setIdempotencyKey(idempotencyKey);

        Map<String, Object> rationale = new LinkedHashMap<>();
        rationale.put("creditScoreId", credit.id().toString());
        rationale.put("creditScore", credit.score());
        rationale.put("riskBand", credit.riskBand().name());
        rationale.put("probabilityOfDefault", credit.probabilityOfDefault());
        rationale.put("recommendedLimit", credit.recommendedLimit());
        rationale.put("availableLimit", availableLimit);
        rationale.put("modelSource", credit.modelSource());
        rationale.put("modelExplanation", credit.explanation());
        rationale.put("modelRecommendation", credit.recommendation());
        rationale.put("aiPredictionId", credit.aiPredictionId());
        rationale.put("checks", checks);

        String outcome;
        if (!bandOk || !noOverdue) {
            advance.setStatus(AdvanceStatus.REJECTED);
            advance.setDecisionType(AdvanceDecisionType.AUTO);
            advance.setDecidedAt(AppClock.now());
            advance.setDecisionNote(!noOverdue ? "Automatically declined: existing overdue advance must be repaid first"
                    : "Automatically declined: CRITICAL credit risk band");
            outcome = "AUTO_REJECTED";
        } else if (scoreOk && amountOk && limitOk && statusOk && modelOk) {
            advance.setStatus(AdvanceStatus.PENDING);
            advance.setDecisionType(AdvanceDecisionType.AUTO);
            advance.setApprovedAmount(amount);
            advance.setDecisionNote("Automatically approved: all credit policy checks passed");
            outcome = "AUTO_APPROVED";
        } else {
            advance.setStatus(AdvanceStatus.PENDING);
            outcome = "REFERRED_TO_FINANCE";
        }
        rationale.put("outcome", outcome);
        advance.setDecisionRationale(rationale);
        advanceRepository.save(advance);

        if ("AUTO_APPROVED".equals(outcome)) {
            advance.setDecidedAt(AppClock.now());
            disburse(advance, requestedBy);
        }

        auditService.record("ADVANCE_REQUESTED", "FLOAT_ADVANCE", advance.getId(), Map.of(
                "agentCode", agent.getAgentCode(), "amount", amount, "termDays", request.termDays(), "outcome", outcome,
                "creditScore", credit.score()));
        switch (outcome) {
            case "AUTO_APPROVED" -> notificationService.notifyAgent(agent, NotificationType.ADVANCE_DECISION, RiskLevel.LOW,
                    "Float advance approved", String.format("Advance %s of %s approved and credited to your e-float. Interest %s, due %s.",
                            advance.getReference(), advance.getApprovedAmount(), advance.getInterestAmount(), advance.getDueDate()),
                    "FLOAT_ADVANCE", advance.getId());
            case "AUTO_REJECTED" -> notificationService.notifyAgent(agent, NotificationType.ADVANCE_DECISION, RiskLevel.MEDIUM,
                    "Float advance declined", advance.getDecisionNote(), "FLOAT_ADVANCE", advance.getId());
            default -> {
                notificationService.notifyAgent(agent, NotificationType.ADVANCE_DECISION, RiskLevel.LOW, "Float advance under review",
                        "Advance " + advance.getReference() + " has been referred to a finance officer for a credit decision.",
                        "FLOAT_ADVANCE", advance.getId());
                notificationService.notifyRole(Role.FINANCE_OFFICER, NotificationType.ADVANCE_PENDING, RiskLevel.MEDIUM,
                        "Advance awaiting decision (" + agent.getAgentCode() + ")",
                        String.format("%s requested %s for %d days. Credit score %d (%s), available limit %s.",
                                agent.getAgentCode(), amount, request.termDays(), credit.score(), credit.riskBand(), availableLimit),
                        "FLOAT_ADVANCE", advance.getId());
            }
        }
        AdvanceResponse response = AdvanceResponse.from(advance, false);
        publish(agent, "ADVANCE_UPDATED", response);
        return response;
    }

    @Transactional
    public AdvanceResponse approve(UUID advanceId, ApproveRequest request, UUID financeUserId) {
        UUID agentId = agentIdOf(advanceId);
        guard.lockAgent(agentId);
        FloatAdvance advance = requireAdvance(advanceId);
        if (advance.getStatus() != AdvanceStatus.PENDING) {
            throw BusinessException.conflict("ADVANCE_NOT_PENDING", "Advance " + advance.getReference() + " is " + advance.getStatus());
        }
        Agent agent = advance.getAgent();
        if (!agent.getStatus().canTransact()) {
            throw BusinessException.unprocessable("AGENT_NOT_ACTIVE", "Agent status " + agent.getStatus() + " does not permit float advances");
        }
        BigDecimal approved = request.approvedAmount() == null ? advance.getRequestedAmount() : Money.requireValidAmount(request.approvedAmount(), "approvedAmount");
        if (approved.compareTo(advance.getRequestedAmount()) > 0) {
            throw BusinessException.unprocessable("APPROVED_EXCEEDS_REQUESTED", "Approved amount cannot exceed the requested amount");
        }
        if (request.termDays() != null) {
            int maxTerm = settings.getInt(SettingKey.ADVANCE_MAX_TERM_DAYS);
            if (request.termDays() > maxTerm) {
                throw BusinessException.unprocessable("ADVANCE_TERM_TOO_LONG", "Maximum advance term is " + maxTerm + " days");
            }
            advance.setTermDays(request.termDays());
        }
        if (request.dailyInterestRate() != null) {
            advance.setDailyInterestRate(request.dailyInterestRate());
        }
        advance.setApprovedAmount(approved);
        advance.setDecisionType(AdvanceDecisionType.MANUAL);
        advance.setDecidedBy(financeUserId);
        advance.setDecidedAt(AppClock.now());
        advance.setDecisionNote(request.note());
        Map<String, Object> rationale = new LinkedHashMap<>(advance.getDecisionRationale());
        rationale.put("manualDecision", "APPROVED");
        Object recommended = rationale.get("availableLimit");
        if (recommended != null && approved.compareTo(new BigDecimal(recommended.toString())) > 0) {
            rationale.put("overridesModelLimit", true);
        }
        advance.setDecisionRationale(rationale);
        disburse(advance, financeUserId);

        auditService.record("ADVANCE_APPROVED", "FLOAT_ADVANCE", advance.getId(), Map.of("agentCode", agent.getAgentCode(),
                "approvedAmount", approved, "dailyRate", advance.getDailyInterestRate(), "termDays", advance.getTermDays(),
                "note", request.note()));
        notificationService.notifyAgent(agent, NotificationType.ADVANCE_DECISION, RiskLevel.LOW, "Float advance approved",
                String.format("Advance %s of %s approved by finance and credited to your e-float. Interest %s, due %s.",
                        advance.getReference(), approved, advance.getInterestAmount(), advance.getDueDate()),
                "FLOAT_ADVANCE", advance.getId());
        feedbackCreditDecision(advance, "APPROVED", financeUserId, request.note());
        AdvanceResponse response = AdvanceResponse.from(advance, false);
        publish(agent, "ADVANCE_UPDATED", response);
        return response;
    }

    @Transactional
    public AdvanceResponse reject(UUID advanceId, String note, UUID financeUserId) {
        UUID agentId = agentIdOf(advanceId);
        guard.lockAgent(agentId);
        FloatAdvance advance = requireAdvance(advanceId);
        if (advance.getStatus() != AdvanceStatus.PENDING) {
            throw BusinessException.conflict("ADVANCE_NOT_PENDING", "Advance " + advance.getReference() + " is " + advance.getStatus());
        }
        advance.setStatus(AdvanceStatus.REJECTED);
        advance.setDecisionType(AdvanceDecisionType.MANUAL);
        advance.setDecidedBy(financeUserId);
        advance.setDecidedAt(AppClock.now());
        advance.setDecisionNote(note);
        advance.setClosedAt(AppClock.now());
        Map<String, Object> rationale = new LinkedHashMap<>(advance.getDecisionRationale());
        rationale.put("manualDecision", "REJECTED");
        advance.setDecisionRationale(rationale);

        auditService.record("ADVANCE_REJECTED", "FLOAT_ADVANCE", advance.getId(),
                Map.of("agentCode", advance.getAgent().getAgentCode(), "note", note));
        notificationService.notifyAgent(advance.getAgent(), NotificationType.ADVANCE_DECISION, RiskLevel.MEDIUM,
                "Float advance declined", "Advance " + advance.getReference() + " was declined: " + note, "FLOAT_ADVANCE", advance.getId());
        feedbackCreditDecision(advance, "REJECTED", financeUserId, note);
        AdvanceResponse response = AdvanceResponse.from(advance, false);
        publish(advance.getAgent(), "ADVANCE_UPDATED", response);
        return response;
    }

    /** Agent repays from its own e-float, or a finance officer records an external settlement. */
    @Transactional
    public RepaymentResult repay(UUID advanceId, BigDecimal requestedAmount, String idempotencyKey, UUID actorId,
                                 RepaymentSource source, UUID requiredAgentId) {
        if (source == RepaymentSource.COMMISSION_DEDUCTION) {
            throw new IllegalArgumentException("Commission deductions are applied by the commission service");
        }
        BigDecimal amount = Money.requireValidAmount(requestedAmount, "amount");
        UUID agentId = agentIdOf(advanceId);
        if (requiredAgentId != null && !requiredAgentId.equals(agentId)) {
            throw BusinessException.forbidden("Advance does not belong to you");
        }
        String hash = AgentTransactionGuard.requestHash("ADVANCE_REPAYMENT", advanceId, amount, source);
        guard.lockAgent(agentId);
        Optional<FinancialTransaction> replay = guard.findReplay(idempotencyKey, hash, actorId);
        if (replay.isPresent()) {
            AdvanceRepayment r = repaymentRepository.findByTransaction_Id(replay.get().getId()).stream().findFirst().orElseThrow();
            return new RepaymentResult(replay.get().getId(), replay.get().getReference(), Money.of(r.getTotalAmount()),
                    Money.of(r.getInterestAmount()), Money.of(r.getPrincipalAmount()),
                    AdvanceResponse.from(requireAdvance(advanceId), true), true);
        }

        FloatAdvance advance = requireAdvance(advanceId);
        if (!advance.getStatus().isOutstanding()) {
            throw BusinessException.unprocessable("ADVANCE_NOT_OUTSTANDING", "Advance " + advance.getReference() + " is " + advance.getStatus());
        }
        if (amount.compareTo(advance.totalOutstanding()) > 0) {
            throw BusinessException.unprocessable("REPAYMENT_EXCEEDS_OUTSTANDING",
                    "Repayment " + amount + " exceeds the outstanding balance of " + advance.totalOutstanding(),
                    Map.of("outstanding", advance.totalOutstanding()));
        }
        RecoveryAllocator.Allocation allocation = RecoveryAllocator.allocate(amount, BigDecimal.valueOf(100),
                advance.getInterestOutstanding(), advance.getPrincipalOutstanding(),
                settings.getRecoveryOrder(SettingKey.ADVANCE_REPAYMENT_ORDER));

        Agent agent = advance.getAgent();
        FinancialTransaction tx = new FinancialTransaction();
        tx.setReference(ReferenceGenerator.next("RPY"));
        tx.setIdempotencyKey(idempotencyKey);
        tx.setRequestHash(hash);
        tx.setType(TransactionType.ADVANCE_REPAYMENT);
        tx.setAgent(agent);
        tx.setAreaId(agent.getArea().getId());
        tx.setAmount(amount);
        tx.setCurrency(properties.getCurrency());
        tx.setDescription("Repayment of float advance " + advance.getReference()
                + (source == RepaymentSource.FINANCE_RECORDED ? " (recorded by finance)" : " from e-float"));
        tx.setInitiatedBy(actorId);

        var sourceAccount = source == RepaymentSource.AGENT_EFLOAT
                ? accounts.agent(agentId, BalanceType.EFLOAT) : accounts.system(BalanceType.SETTLEMENT_BANK);
        ledgerService.post(tx, JournalBuilder.journal()
                .debit(sourceAccount, amount, source == RepaymentSource.AGENT_EFLOAT ? "Advance repayment from e-float"
                        : "Advance repayment received via settlement bank")
                .credit(accounts.agent(agentId, BalanceType.ADVANCE_INTEREST), allocation.interestRecovered(), "Interest repaid")
                .credit(accounts.agent(agentId, BalanceType.ADVANCE_PRINCIPAL), allocation.principalRecovered(), "Principal repaid")
                .legs());
        recoveryService.applyToAdvance(advance, allocation.interestRecovered(), allocation.principalRecovered(), tx, source, null, actorId);

        auditService.record("ADVANCE_REPAYMENT", "FLOAT_ADVANCE", advance.getId(), Map.of("agentCode", agent.getAgentCode(),
                "amount", amount, "interest", allocation.interestRecovered(), "principal", allocation.principalRecovered(),
                "source", source.name(), "reference", tx.getReference()));
        notificationService.notifyAgent(agent, NotificationType.ADVANCE_REPAYMENT, RiskLevel.LOW, "Advance repayment received",
                String.format("%s applied to advance %s (interest %s, principal %s). Outstanding: %s.", amount,
                        advance.getReference(), allocation.interestRecovered(), allocation.principalRecovered(), advance.totalOutstanding()),
                "FLOAT_ADVANCE", advance.getId());
        AdvanceResponse response = AdvanceResponse.from(advance, false);
        publish(agent, "ADVANCE_UPDATED", response);
        return new RepaymentResult(tx.getId(), tx.getReference(), amount, allocation.interestRecovered(),
                allocation.principalRecovered(), response, false);
    }

    /** Marks past-due advances overdue and accrues daily penalty interest on overdue principal. One transaction per advance. */
    public int accruePenalties() {
        LocalDate today = AppClock.today();
        int processed = 0;
        for (UUID id : advanceRepository.findIdsPastDue(EnumSet.of(AdvanceStatus.ACTIVE, AdvanceStatus.OVERDUE), today)) {
            try {
                Boolean changed = transactionTemplate.execute(status -> accrueFor(id, today));
                if (Boolean.TRUE.equals(changed)) {
                    processed++;
                }
            } catch (RuntimeException ex) {
                log.error("Penalty accrual failed for advance {}: {}", id, ex.getMessage(), ex);
            }
        }
        return processed;
    }

    private boolean accrueFor(UUID advanceId, LocalDate today) {
        UUID agentId = agentIdOf(advanceId);
        guard.lockAgent(agentId);
        FloatAdvance advance = requireAdvance(advanceId);
        if (!advance.getStatus().isOutstanding() || advance.getDueDate() == null || !today.isAfter(advance.getDueDate())) {
            return false;
        }
        Agent agent = advance.getAgent();
        boolean changed = false;
        if (advance.getStatus() == AdvanceStatus.ACTIVE) {
            advance.setStatus(AdvanceStatus.OVERDUE);
            changed = true;
            String msg = String.format("Advance %s (%s) was due on %s. Outstanding %s.", advance.getReference(),
                    agent.getAgentCode(), advance.getDueDate(), advance.totalOutstanding());
            notificationService.notifyAgent(agent, NotificationType.ADVANCE_OVERDUE, RiskLevel.HIGH, "Float advance overdue", msg,
                    "FLOAT_ADVANCE", advance.getId());
            notificationService.notifyRole(Role.FINANCE_OFFICER, NotificationType.ADVANCE_OVERDUE, RiskLevel.HIGH,
                    "Advance overdue (" + agent.getAgentCode() + ")", msg, "FLOAT_ADVANCE", advance.getId());
            notificationService.notifyAreaManagers(agent.getArea().getId(), NotificationType.ADVANCE_OVERDUE, RiskLevel.HIGH,
                    "Advance overdue (" + agent.getAgentCode() + ")", msg, "FLOAT_ADVANCE", advance.getId());
        }
        LocalDate from = advance.getLastPenaltyAccrualDate() != null && advance.getLastPenaltyAccrualDate().isAfter(advance.getDueDate())
                ? advance.getLastPenaltyAccrualDate() : advance.getDueDate();
        long days = ChronoUnit.DAYS.between(from, today);
        BigDecimal penalty = InterestCalculator.flatInterest(advance.getPrincipalOutstanding(),
                settings.getDecimal(SettingKey.ADVANCE_PENALTY_DAILY_RATE), days);
        if (penalty.signum() > 0) {
            FinancialTransaction tx = new FinancialTransaction();
            tx.setReference(ReferenceGenerator.next("PEN"));
            tx.setIdempotencyKey("ADV-PEN-" + advance.getId() + "-" + today);
            tx.setType(TransactionType.ADVANCE_PENALTY_INTEREST);
            tx.setAgent(agent);
            tx.setAreaId(agent.getArea().getId());
            tx.setAmount(penalty);
            tx.setCurrency(properties.getCurrency());
            tx.setDescription(String.format("Penalty interest on overdue advance %s: %d day(s) on principal %s",
                    advance.getReference(), days, Money.of(advance.getPrincipalOutstanding())));
            ledgerService.post(tx, JournalBuilder.journal()
                    .pair(accounts.agent(agentId, BalanceType.ADVANCE_INTEREST), accounts.system(BalanceType.INTEREST_INCOME),
                            penalty, "Overdue penalty interest")
                    .legs());
            advance.setInterestOutstanding(Money.of(advance.getInterestOutstanding().add(penalty)));
            advance.setPenaltyInterestAccrued(Money.of(advance.getPenaltyInterestAccrued().add(penalty)));
            changed = true;
        }
        if (days > 0) {
            advance.setLastPenaltyAccrualDate(today);
        }
        if (changed) {
            publish(agent, "ADVANCE_UPDATED", AdvanceResponse.from(advance, false));
        }
        return changed;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdvanceResponse> list(UUID agentId, UUID areaId, AdvanceStatus status, Pageable pageable) {
        Specification<FloatAdvance> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (agentId != null) {
                p.add(cb.equal(root.get("agent").get("id"), agentId));
            }
            if (areaId != null) {
                p.add(cb.equal(root.get("agent").get("area").get("id"), areaId));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(advanceRepository.findAll(spec, pageable), a -> AdvanceResponse.from(a, false));
    }

    @Transactional(readOnly = true)
    public AdvanceDetail detail(UUID advanceId) {
        FloatAdvance advance = requireAdvance(advanceId);
        return new AdvanceDetail(AdvanceResponse.from(advance, false),
                repaymentRepository.findByAdvance_IdOrderByCreatedAtAsc(advanceId).stream().map(RepaymentView::from).toList());
    }

    @Transactional(readOnly = true)
    public UUID agentIdOf(UUID advanceId) {
        return advanceRepository.findAgentIdById(advanceId)
                .orElseThrow(() -> BusinessException.notFound("Float advance", advanceId));
    }

    private void disburse(FloatAdvance advance, UUID actorId) {
        Agent agent = advance.getAgent();
        BigDecimal principal = Money.of(advance.getApprovedAmount());
        BigDecimal interest = InterestCalculator.flatInterest(principal, advance.getDailyInterestRate(), advance.getTermDays());

        FinancialTransaction tx = new FinancialTransaction();
        tx.setReference(ReferenceGenerator.next("ADV"));
        tx.setIdempotencyKey("ADV-DISB-" + advance.getId());
        tx.setType(TransactionType.ADVANCE_DISBURSEMENT);
        tx.setAgent(agent);
        tx.setAreaId(agent.getArea().getId());
        tx.setAmount(principal);
        tx.setCurrency(properties.getCurrency());
        tx.setDescription(String.format("Float advance %s: principal %s, interest %s (%s/day x %d days)", advance.getReference(),
                principal, interest, advance.getDailyInterestRate().stripTrailingZeros().toPlainString(), advance.getTermDays()));
        tx.setInitiatedBy(actorId);

        UUID agentId = agent.getId();
        ledgerService.post(tx, JournalBuilder.journal()
                .pair(accounts.agent(agentId, BalanceType.ADVANCE_PRINCIPAL), accounts.agent(agentId, BalanceType.EFLOAT),
                        principal, "Advance principal credited to e-float")
                .pair(accounts.agent(agentId, BalanceType.ADVANCE_INTEREST), accounts.system(BalanceType.INTEREST_INCOME),
                        interest, "Advance interest obligation recorded")
                .legs());

        advance.setStatus(AdvanceStatus.ACTIVE);
        advance.setInterestAmount(interest);
        advance.setPrincipalOutstanding(principal);
        advance.setInterestOutstanding(interest);
        advance.setDisbursedAt(tx.getCreatedAt());
        advance.setDueDate(AppClock.today().plusDays(advance.getTermDays()));
        advance.setDisbursementTransaction(tx);
    }

    private void feedbackCreditDecision(FloatAdvance advance, String decision, UUID financeUserId, String note) {
        Object predictionId = advance.getDecisionRationale() == null ? null : advance.getDecisionRationale().get("aiPredictionId");
        if (predictionId == null) {
            return;
        }
        aiGovernanceFeedback.recordDecision(predictionId.toString(), decision,
                financeUserId == null ? "unknown" : financeUserId.toString(), "FINANCE_OFFICER", note, advance.getReference());
    }

    private boolean check(List<Map<String, Object>> checks, String code, boolean passed, String detail) {
        checks.add(Map.of("check", code, "passed", passed, "detail", detail));
        return passed;
    }

    private void publish(Agent agent, String type, Object payload) {
        events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.agent(agent.getId()), type, payload));
        events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.FINANCE, type, payload));
    }

    private Agent requireAgent(UUID agentId) {
        return agentRepository.findWithDetailsById(agentId).orElseThrow(() -> BusinessException.notFound("Agent", agentId));
    }

    private FloatAdvance requireAdvance(UUID advanceId) {
        return advanceRepository.findDetailedById(advanceId).orElseThrow(() -> BusinessException.notFound("Float advance", advanceId));
    }
}
