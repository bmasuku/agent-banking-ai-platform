package com.agentbanking.platform.floatmgmt;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.agent.AgentRepository;
import com.agentbanking.platform.area.Area;
import com.agentbanking.platform.area.AreaRepository;
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
import com.agentbanking.platform.transaction.TransactionType;
import com.agentbanking.platform.user.UserRepository;
import com.agentbanking.platform.websocket.RealtimeEvent;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Float replenishment from area allocation pools to agents, and funding of area pools by finance.
 * The source pool decreases by exactly the amount the agent receives, in one balanced journal.
 */
@Service
@RequiredArgsConstructor
public class FloatManagementService {

    private final FloatReplenishmentRepository repository;
    private final AgentRepository agentRepository;
    private final AreaRepository areaRepository;
    private final UserRepository userRepository;
    private final AgentTransactionGuard guard;
    private final LedgerService ledgerService;
    private final LedgerAccountService accounts;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final AppProperties properties;

    public record ReplenishRequest(@NotNull ReplenishmentType type, @NotNull @DecimalMin("1.00") BigDecimal amount,
                                   @Size(max = 255) String reason, UUID predictionId) {
    }

    public record FundPoolRequest(@DecimalMin("0.00") BigDecimal efloatAmount, @DecimalMin("0.00") BigDecimal cashAmount,
                                  @Size(max = 255) String reason) {
    }

    public record ReplenishmentResponse(UUID id, String reference, UUID agentId, String agentCode, UUID areaId, String areaCode,
                                        ReplenishmentType type, BigDecimal amount, String sourceAccountCode,
                                        BigDecimal sourceBalanceAfter, String reason, UUID predictionId, String performedBy,
                                        UUID transactionId, Instant createdAt, BalanceSnapshot agentBalancesAfter,
                                        boolean replayed) {
    }

    public record PoolFundingResponse(UUID transactionId, String reference, UUID areaId, String areaCode, BigDecimal efloatAmount,
                                      BigDecimal cashAmount, BigDecimal efloatPoolBalance, BigDecimal cashPoolBalance,
                                      boolean replayed) {
    }

    public record VaultFundingRequest(@NotNull @DecimalMin("1.00") BigDecimal amount, @Size(max = 255) String reason) {
    }

    public record VaultFundingResponse(UUID transactionId, String reference, BigDecimal amount, BigDecimal vaultBalance,
                                       boolean replayed) {
    }

    /** Area manager replenishes one of their agents from the area pool. Area authorization is checked by the caller. */
    @Transactional
    public ReplenishmentResponse replenish(UUID agentId, ReplenishRequest request, String idempotencyKey, UUID managerId) {
        BigDecimal amount = Money.requireValidAmount(request.amount(), "amount");
        String hash = AgentTransactionGuard.requestHash("FLOAT_REPLENISHMENT", agentId, request.type(), amount);
        guard.lockAgent(agentId);
        Optional<FinancialTransaction> replay = guard.findReplay(idempotencyKey, hash, managerId);
        if (replay.isPresent()) {
            FloatReplenishment existing = repository.findByTransaction_Id(replay.get().getId()).orElseThrow();
            return toResponse(existing, true);
        }

        Agent agent = agentRepository.findWithDetailsById(agentId).orElseThrow(() -> BusinessException.notFound("Agent", agentId));
        if (!agent.getStatus().canTransact()) {
            throw BusinessException.unprocessable("AGENT_NOT_ACTIVE", "Agent " + agent.getAgentCode() + " is " + agent.getStatus());
        }
        Area area = agent.getArea();
        boolean efloat = request.type() == ReplenishmentType.EFLOAT;
        LedgerAccount pool = accounts.area(area.getId(), efloat ? BalanceType.AREA_EFLOAT_POOL : BalanceType.AREA_CASH_POOL);
        LedgerAccount target = accounts.agent(agentId, efloat ? BalanceType.EFLOAT : BalanceType.CASH);

        FinancialTransaction tx = new FinancialTransaction();
        tx.setReference(ReferenceGenerator.next("RPL"));
        tx.setIdempotencyKey(idempotencyKey);
        tx.setRequestHash(hash);
        tx.setType(TransactionType.FLOAT_REPLENISHMENT);
        tx.setAgent(agent);
        tx.setAreaId(area.getId());
        tx.setAmount(amount);
        tx.setCurrency(properties.getCurrency());
        tx.setDescription((efloat ? "E-float" : "Cash") + " replenishment of " + amount + " from " + area.getCode() + " pool"
                + (request.reason() == null || request.reason().isBlank() ? "" : ": " + request.reason().trim()));
        tx.setInitiatedBy(managerId);

        // e-float pool is a liability (debit reduces it); cash pool is an asset (credit reduces it)
        JournalBuilder journal = efloat
                ? JournalBuilder.journal().pair(pool, target, amount, "E-float allocated from area pool")
                : JournalBuilder.journal().pair(target, pool, amount, "Cash delivered from area vault");
        LedgerService.PostingResult result = ledgerService.post(tx, journal.legs());

        FloatReplenishment r = new FloatReplenishment();
        r.setReference(tx.getReference());
        r.setAgent(agent);
        r.setArea(area);
        r.setReplenishmentType(request.type());
        r.setAmount(amount);
        r.setSourceAccount(pool);
        r.setSourceBalanceAfter(Money.of(result.balanceOf(pool)));
        r.setReason(request.reason());
        r.setPredictionId(request.predictionId());
        r.setPerformedBy(userRepository.getReferenceById(managerId));
        r.setTransaction(tx);
        repository.save(r);

        auditService.record("FLOAT_REPLENISHED", "FLOAT_REPLENISHMENT", r.getId(), Map.of("agentCode", agent.getAgentCode(),
                "type", request.type().name(), "amount", amount, "reference", tx.getReference()));
        notificationService.notifyAgent(agent, NotificationType.REPLENISHMENT_RECEIVED, RiskLevel.LOW,
                (efloat ? "E-float" : "Cash") + " replenishment received",
                String.format("Your area manager added %s %s to your %s.", amount, tx.getCurrency(), efloat ? "e-float" : "cash in hand"),
                "FLOAT_REPLENISHMENT", r.getId());
        ReplenishmentResponse response = toResponse(r, false);
        events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.agent(agentId), "REPLENISHMENT_RECEIVED", response));
        events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.area(area.getId()), "REPLENISHMENT_COMPLETED", response));
        return response;
    }

    /**
     * Recalls e-float or cash from an agent back to the area pool (inverse of {@link #replenish}).
     * Used by demo balance alignment and by area managers correcting over-allocation; posts a balanced journal.
     */
    @Transactional
    public ReplenishmentResponse recall(UUID agentId, ReplenishRequest request, String idempotencyKey, UUID managerId) {
        BigDecimal amount = Money.requireValidAmount(request.amount(), "amount");
        String hash = AgentTransactionGuard.requestHash("FLOAT_RECALL", agentId, request.type(), amount);
        guard.lockAgent(agentId);
        Optional<FinancialTransaction> replay = guard.findReplay(idempotencyKey, hash, managerId);
        if (replay.isPresent()) {
            FloatReplenishment existing = repository.findByTransaction_Id(replay.get().getId()).orElseThrow();
            return toResponse(existing, true);
        }

        Agent agent = agentRepository.findWithDetailsById(agentId).orElseThrow(() -> BusinessException.notFound("Agent", agentId));
        if (!agent.getStatus().canTransact()) {
            throw BusinessException.unprocessable("AGENT_NOT_ACTIVE", "Agent " + agent.getAgentCode() + " is " + agent.getStatus());
        }
        Area area = agent.getArea();
        boolean efloat = request.type() == ReplenishmentType.EFLOAT;
        LedgerAccount pool = accounts.area(area.getId(), efloat ? BalanceType.AREA_EFLOAT_POOL : BalanceType.AREA_CASH_POOL);
        LedgerAccount source = accounts.agent(agentId, efloat ? BalanceType.EFLOAT : BalanceType.CASH);
        BigDecimal available = Money.of(source.getBalance());
        if (available.compareTo(amount) < 0) {
            throw BusinessException.unprocessable("INSUFFICIENT_FLOAT",
                    "Agent " + agent.getAgentCode() + " holds only " + available + " " + (efloat ? "e-float" : "cash"),
                    Map.of("available", available, "requested", amount));
        }

        FinancialTransaction tx = new FinancialTransaction();
        tx.setReference(ReferenceGenerator.next("RCL"));
        tx.setIdempotencyKey(idempotencyKey);
        tx.setRequestHash(hash);
        tx.setType(TransactionType.FLOAT_RECALL);
        tx.setAgent(agent);
        tx.setAreaId(area.getId());
        tx.setAmount(amount);
        tx.setCurrency(properties.getCurrency());
        tx.setDescription((efloat ? "E-float" : "Cash") + " recall of " + amount + " to " + area.getCode() + " pool"
                + (request.reason() == null || request.reason().isBlank() ? "" : ": " + request.reason().trim()));
        tx.setInitiatedBy(managerId);

        // Reverse of replenishment journals
        JournalBuilder journal = efloat
                ? JournalBuilder.journal().pair(source, pool, amount, "E-float recalled to area pool")
                : JournalBuilder.journal().pair(pool, source, amount, "Cash recalled to area vault");
        LedgerService.PostingResult result = ledgerService.post(tx, journal.legs());

        FloatReplenishment r = new FloatReplenishment();
        r.setReference(tx.getReference());
        r.setAgent(agent);
        r.setArea(area);
        r.setReplenishmentType(request.type());
        r.setAmount(amount); // positive; direction is conveyed by FLOAT_RECALL transaction type
        r.setSourceAccount(pool);
        r.setSourceBalanceAfter(Money.of(result.balanceOf(pool)));
        r.setReason(request.reason());
        r.setPredictionId(request.predictionId());
        r.setPerformedBy(userRepository.getReferenceById(managerId));
        r.setTransaction(tx);
        repository.save(r);

        auditService.record("FLOAT_RECALLED", "FLOAT_RECALL", r.getId(), Map.of("agentCode", agent.getAgentCode(),
                "type", request.type().name(), "amount", amount, "reference", tx.getReference()));
        notificationService.notifyAgent(agent, NotificationType.REPLENISHMENT_RECEIVED, RiskLevel.LOW,
                (efloat ? "E-float" : "Cash") + " recalled to area pool",
                String.format("%s %s was returned from your %s to the area pool.", amount, tx.getCurrency(),
                        efloat ? "e-float" : "cash in hand"),
                "FLOAT_RECALL", r.getId());
        ReplenishmentResponse response = toResponse(r, false);
        events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.agent(agentId), "FLOAT_RECALLED", response));
        events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.area(area.getId()), "FLOAT_RECALLED", response));
        return response;
    }

    /** Finance tops up an area's allocation pools from the settlement bank and central cash vault. */
    @Transactional
    public PoolFundingResponse fundAreaPool(UUID areaId, FundPoolRequest request, String idempotencyKey, UUID financeUserId) {
        BigDecimal efloatAmount = request.efloatAmount() == null ? Money.ZERO : Money.of(request.efloatAmount());
        BigDecimal cashAmount = request.cashAmount() == null ? Money.ZERO : Money.of(request.cashAmount());
        if (efloatAmount.signum() <= 0 && cashAmount.signum() <= 0) {
            throw BusinessException.badRequest("INVALID_AMOUNT", "Provide a positive efloatAmount and/or cashAmount");
        }
        Area area = areaRepository.findById(areaId).orElseThrow(() -> BusinessException.notFound("Area", areaId));
        String hash = AgentTransactionGuard.requestHash("AREA_POOL_FUNDING", areaId, efloatAmount, cashAmount);
        Optional<FinancialTransaction> replay = guard.findReplay(idempotencyKey, hash, financeUserId);
        if (replay.isPresent()) {
            return new PoolFundingResponse(replay.get().getId(), replay.get().getReference(), areaId, area.getCode(), efloatAmount,
                    cashAmount, accounts.areaBalance(areaId, BalanceType.AREA_EFLOAT_POOL),
                    accounts.areaBalance(areaId, BalanceType.AREA_CASH_POOL), true);
        }

        FinancialTransaction tx = new FinancialTransaction();
        tx.setReference(ReferenceGenerator.next("FND"));
        tx.setIdempotencyKey(idempotencyKey);
        tx.setRequestHash(hash);
        tx.setType(TransactionType.AREA_POOL_FUNDING);
        tx.setAreaId(areaId);
        tx.setAmount(efloatAmount.add(cashAmount));
        tx.setCurrency(properties.getCurrency());
        tx.setDescription("Area pool funding for " + area.getCode() + ": e-float " + efloatAmount + ", cash " + cashAmount
                + (request.reason() == null || request.reason().isBlank() ? "" : " - " + request.reason().trim()));
        tx.setInitiatedBy(financeUserId);

        LedgerService.PostingResult result = ledgerService.post(tx, JournalBuilder.journal()
                .pair(accounts.system(BalanceType.SETTLEMENT_BANK), accounts.area(areaId, BalanceType.AREA_EFLOAT_POOL),
                        efloatAmount, "E-float issued to area pool against settlement bank")
                .pair(accounts.area(areaId, BalanceType.AREA_CASH_POOL), accounts.system(BalanceType.CENTRAL_CASH_VAULT),
                        cashAmount, "Cash transferred from central vault to area vault")
                .legs());

        auditService.record("AREA_POOL_FUNDED", "AREA", areaId, Map.of("areaCode", area.getCode(), "efloat", efloatAmount,
                "cash", cashAmount, "reference", tx.getReference()));
        PoolFundingResponse response = new PoolFundingResponse(tx.getId(), tx.getReference(), areaId, area.getCode(), efloatAmount,
                cashAmount, Money.of(result.balanceOf(accounts.area(areaId, BalanceType.AREA_EFLOAT_POOL))),
                Money.of(result.balanceOf(accounts.area(areaId, BalanceType.AREA_CASH_POOL))), false);
        events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.area(areaId), "AREA_POOL_FUNDED", response));
        events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.FINANCE, "AREA_POOL_FUNDED", response));
        return response;
    }

    /** Records simulated physical cash capital placed into the central vault (DR vault / CR opening equity). */
    @Transactional
    public VaultFundingResponse fundCentralVault(VaultFundingRequest request, String idempotencyKey, UUID financeUserId) {
        BigDecimal amount = Money.requireValidAmount(request.amount(), "amount");
        String hash = AgentTransactionGuard.requestHash("VAULT_CAPITAL", amount);
        Optional<FinancialTransaction> replay = guard.findReplay(idempotencyKey, hash, financeUserId);
        if (replay.isPresent()) {
            return new VaultFundingResponse(replay.get().getId(), replay.get().getReference(), amount,
                    Money.of(accounts.system(BalanceType.CENTRAL_CASH_VAULT).getBalance()), true);
        }
        FinancialTransaction tx = new FinancialTransaction();
        tx.setReference(ReferenceGenerator.next("CAP"));
        tx.setIdempotencyKey(idempotencyKey);
        tx.setRequestHash(hash);
        tx.setType(TransactionType.OPENING_BALANCE);
        tx.setAmount(amount);
        tx.setCurrency(properties.getCurrency());
        tx.setDescription("Cash capital placed in central vault"
                + (request.reason() == null || request.reason().isBlank() ? "" : " - " + request.reason().trim()));
        tx.setInitiatedBy(financeUserId);
        LedgerAccount vault = accounts.system(BalanceType.CENTRAL_CASH_VAULT);
        LedgerService.PostingResult result = ledgerService.post(tx, JournalBuilder.journal()
                .pair(vault, accounts.system(BalanceType.OPENING_EQUITY), amount, "Simulated cash capital injection")
                .legs());
        auditService.record("CENTRAL_VAULT_FUNDED", "LEDGER_ACCOUNT", vault.getId(), Map.of("amount", amount,
                "reference", tx.getReference()));
        return new VaultFundingResponse(tx.getId(), tx.getReference(), amount, Money.of(result.balanceOf(vault)), false);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReplenishmentResponse> list(UUID areaId, UUID agentId, Instant from, Instant to, Pageable pageable) {
        Specification<FloatReplenishment> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (areaId != null) {
                p.add(cb.equal(root.get("area").get("id"), areaId));
            }
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
        return PageResponse.of(repository.findAll(spec, pageable), r -> toResponse(r, false, null));
    }

    private ReplenishmentResponse toResponse(FloatReplenishment r, boolean replayed) {
        return toResponse(r, replayed, accounts.agentBalances(r.getAgent().getId()));
    }

    private ReplenishmentResponse toResponse(FloatReplenishment r, boolean replayed, BalanceSnapshot balances) {
        return new ReplenishmentResponse(r.getId(), r.getReference(), r.getAgent().getId(), r.getAgent().getAgentCode(),
                r.getArea().getId(), r.getArea().getCode(), r.getReplenishmentType(), Money.of(r.getAmount()),
                r.getSourceAccount().getCode(), Money.of(r.getSourceBalanceAfter()), r.getReason(), r.getPredictionId(),
                r.getPerformedBy().getFullName(), r.getTransaction().getId(), r.getCreatedAt(), balances, replayed);
    }
}
