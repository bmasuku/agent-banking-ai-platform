package com.agentbanking.platform.exchange;

import com.agentbanking.platform.admin.SettingKey;
import com.agentbanking.platform.admin.SystemSettingService;
import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.agent.AgentRepository;
import com.agentbanking.platform.audit.AuditService;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.GeoUtils;
import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.ReferenceGenerator;
import com.agentbanking.platform.common.RiskLevel;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.config.AppProperties;
import com.agentbanking.platform.ledger.BalanceSnapshot;
import com.agentbanking.platform.ledger.BalanceType;
import com.agentbanking.platform.ledger.JournalBuilder;
import com.agentbanking.platform.ledger.LedgerAccountService;
import com.agentbanking.platform.ledger.LedgerService;
import com.agentbanking.platform.notification.NotificationService;
import com.agentbanking.platform.notification.NotificationType;
import com.agentbanking.platform.transaction.AgentTransactionGuard;
import com.agentbanking.platform.transaction.FinancialTransaction;
import com.agentbanking.platform.transaction.TransactionType;
import com.agentbanking.platform.websocket.RealtimeEvent;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Agent-to-agent float exchange. A request is proposed by one agent and accepted by the counterparty;
 * acceptance locks both agent accounts (in id order) and posts one four-leg journal so both agents'
 * e-float and cash change atomically.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FloatExchangeService {

    private final FloatExchangeRepository repository;
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

    public record ProposeRequest(@NotNull UUID counterpartyAgentId, @NotNull ExchangeDirection direction,
                                 @NotNull @DecimalMin("1.00") BigDecimal amount, @Size(max = 255) String note) {
    }

    public record RespondRequest(@Size(max = 255) String note) {
    }

    public record ExchangeResponse(UUID id, String reference, UUID requesterAgentId, String requesterAgentCode,
                                   String requesterBusinessName, UUID counterpartyAgentId, String counterpartyAgentCode,
                                   String counterpartyBusinessName, ExchangeDirection direction, BigDecimal amount,
                                   ExchangeStatus status, String note, String responseNote, UUID transactionId,
                                   String transactionReference, Instant requestedAt, Instant expiresAt, Instant respondedAt,
                                   String requesterGives, String requesterReceives, boolean replayed) {
        static ExchangeResponse from(FloatExchange e, boolean replayed) {
            boolean efloatForCash = e.getDirection() == ExchangeDirection.EFLOAT_FOR_CASH;
            return new ExchangeResponse(e.getId(), e.getReference(), e.getRequester().getId(), e.getRequester().getAgentCode(),
                    e.getRequester().getBusinessName(), e.getCounterparty().getId(), e.getCounterparty().getAgentCode(),
                    e.getCounterparty().getBusinessName(), e.getDirection(), Money.of(e.getAmount()), e.getStatus(), e.getNote(),
                    e.getResponseNote(), e.getTransaction() == null ? null : e.getTransaction().getId(),
                    e.getTransaction() == null ? null : e.getTransaction().getReference(), e.getRequestedAt(), e.getExpiresAt(),
                    e.getRespondedAt(), efloatForCash ? "EFLOAT" : "CASH", efloatForCash ? "CASH" : "EFLOAT", replayed);
        }
    }

    /**
     * Nearby agent that could act as an exchange counterparty. Exact balances of other agents are not
     * disclosed; only liquidity levels relative to their own operating thresholds.
     */
    public record PeerResponse(UUID agentId, String agentCode, String businessName, String address, BigDecimal latitude,
                               BigDecimal longitude, BigDecimal distanceKm, String efloatLiquidity, String cashLiquidity) {
    }

    @Transactional
    public ExchangeResponse propose(UUID requesterAgentId, ProposeRequest request, String idempotencyKey, UUID initiatorId) {
        AgentTransactionGuard.validateKey(idempotencyKey);
        BigDecimal amount = Money.requireValidAmount(request.amount(), "amount");
        if (requesterAgentId.equals(request.counterpartyAgentId())) {
            throw BusinessException.badRequest("INVALID_COUNTERPARTY", "An agent cannot exchange float with itself");
        }
        guard.lockAgent(requesterAgentId);
        Optional<FloatExchange> existing = repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            FloatExchange e = existing.get();
            if (!e.getRequester().getId().equals(requesterAgentId) || e.getAmount().compareTo(amount) != 0
                    || e.getDirection() != request.direction() || !e.getCounterparty().getId().equals(request.counterpartyAgentId())) {
                throw BusinessException.conflict("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key was already used for a different request");
            }
            return ExchangeResponse.from(e, true);
        }
        Agent requester = requireAgent(requesterAgentId);
        Agent counterparty = requireAgent(request.counterpartyAgentId());
        requireActive(requester);
        requireActive(counterparty);
        if (!requester.getArea().getId().equals(counterparty.getArea().getId())) {
            throw BusinessException.unprocessable("COUNTERPARTY_OUT_OF_AREA", "Float exchanges are limited to agents in the same area");
        }
        BalanceSnapshot balances = accounts.agentBalances(requesterAgentId);
        BigDecimal giving = request.direction() == ExchangeDirection.EFLOAT_FOR_CASH ? balances.efloat() : balances.cash();
        if (giving.compareTo(amount) < 0) {
            throw BusinessException.unprocessable(request.direction() == ExchangeDirection.EFLOAT_FOR_CASH ? "INSUFFICIENT_EFLOAT" : "INSUFFICIENT_CASH",
                    "You do not have enough " + (request.direction() == ExchangeDirection.EFLOAT_FOR_CASH ? "e-float" : "cash")
                            + " to offer " + amount, Map.of("available", giving));
        }

        FloatExchange e = new FloatExchange();
        e.setReference(ReferenceGenerator.next("EXC"));
        e.setRequester(requester);
        e.setCounterparty(counterparty);
        e.setDirection(request.direction());
        e.setAmount(amount);
        e.setNote(request.note());
        e.setIdempotencyKey(idempotencyKey);
        e.setRequestedAt(AppClock.now());
        e.setExpiresAt(AppClock.now().plus(Duration.ofHours(settings.getInt(SettingKey.EXCHANGE_EXPIRY_HOURS))));
        repository.save(e);

        auditService.record("EXCHANGE_PROPOSED", "FLOAT_EXCHANGE", e.getId(), Map.of("requester", requester.getAgentCode(),
                "counterparty", counterparty.getAgentCode(), "direction", request.direction().name(), "amount", amount));
        boolean efloatForCash = request.direction() == ExchangeDirection.EFLOAT_FOR_CASH;
        notificationService.notifyAgent(counterparty, NotificationType.EXCHANGE_REQUEST, RiskLevel.MEDIUM,
                "Float exchange request from " + requester.getAgentCode(),
                String.format("%s (%s) offers %s %s in exchange for your %s. Expires %s.", requester.getBusinessName(),
                        requester.getAgentCode(), amount, efloatForCash ? "e-float" : "cash", efloatForCash ? "cash" : "e-float",
                        e.getExpiresAt()),
                "FLOAT_EXCHANGE", e.getId());
        ExchangeResponse response = ExchangeResponse.from(e, false);
        publish(e, "EXCHANGE_PROPOSED", response);
        return response;
    }

    @Transactional
    public ExchangeResponse accept(UUID exchangeId, UUID counterpartyAgentId, String responseNote, UUID initiatorId) {
        UUID[] participants = participants(exchangeId);
        if (!participants[1].equals(counterpartyAgentId)) {
            throw BusinessException.forbidden("Only the counterparty agent can accept this exchange");
        }
        guard.lockAgents(participants[0], participants[1]);
        FloatExchange e = requireExchange(exchangeId);
        if (e.getStatus() == ExchangeStatus.COMPLETED) {
            return ExchangeResponse.from(e, true);
        }
        requirePending(e);
        if (AppClock.now().isAfter(e.getExpiresAt())) {
            throw BusinessException.unprocessable("EXCHANGE_EXPIRED", "Exchange " + e.getReference() + " has expired");
        }
        Agent requester = e.getRequester();
        Agent counterparty = e.getCounterparty();
        requireActive(requester);
        requireActive(counterparty);

        FinancialTransaction tx = new FinancialTransaction();
        tx.setReference(ReferenceGenerator.next("EXC"));
        tx.setIdempotencyKey("EXC-" + e.getId());
        tx.setType(TransactionType.FLOAT_EXCHANGE);
        tx.setAgent(requester);
        tx.setCounterpartyAgent(counterparty);
        tx.setAreaId(requester.getArea().getId());
        tx.setAmount(e.getAmount());
        tx.setCurrency(properties.getCurrency());
        tx.setDescription(String.format("Float exchange %s: %s gives %s %s to %s for %s", e.getReference(), requester.getAgentCode(),
                e.getAmount(), e.getDirection() == ExchangeDirection.EFLOAT_FOR_CASH ? "e-float" : "cash", counterparty.getAgentCode(),
                e.getDirection() == ExchangeDirection.EFLOAT_FOR_CASH ? "cash" : "e-float"));
        tx.setInitiatedBy(initiatorId);

        var reqEfloat = accounts.agent(requester.getId(), BalanceType.EFLOAT);
        var reqCash = accounts.agent(requester.getId(), BalanceType.CASH);
        var cpEfloat = accounts.agent(counterparty.getId(), BalanceType.EFLOAT);
        var cpCash = accounts.agent(counterparty.getId(), BalanceType.CASH);
        BigDecimal amount = e.getAmount();
        JournalBuilder journal = e.getDirection() == ExchangeDirection.EFLOAT_FOR_CASH
                // e-float is a liability (debit decreases); cash is an asset (debit increases)
                ? JournalBuilder.journal()
                .pair(reqEfloat, cpEfloat, amount, "E-float from requester to counterparty")
                .pair(reqCash, cpCash, amount, "Cash from counterparty to requester")
                : JournalBuilder.journal()
                .pair(cpEfloat, reqEfloat, amount, "E-float from counterparty to requester")
                .pair(cpCash, reqCash, amount, "Cash from requester to counterparty");
        ledgerService.post(tx, journal.legs());

        e.setStatus(ExchangeStatus.COMPLETED);
        e.setTransaction(tx);
        e.setResponseNote(responseNote);
        e.setRespondedAt(AppClock.now());

        auditService.record("EXCHANGE_COMPLETED", "FLOAT_EXCHANGE", e.getId(), Map.of("reference", e.getReference(),
                "transaction", tx.getReference(), "amount", amount));
        notificationService.notifyAgent(requester, NotificationType.EXCHANGE_UPDATE, RiskLevel.LOW, "Float exchange completed",
                String.format("%s accepted exchange %s for %s. Your balances have been updated.", counterparty.getAgentCode(),
                        e.getReference(), amount), "FLOAT_EXCHANGE", e.getId());
        ExchangeResponse response = ExchangeResponse.from(e, false);
        publish(e, "EXCHANGE_COMPLETED", response);
        return response;
    }

    @Transactional
    public ExchangeResponse reject(UUID exchangeId, UUID counterpartyAgentId, String responseNote) {
        FloatExchange e = requireExchange(exchangeId);
        if (!e.getCounterparty().getId().equals(counterpartyAgentId)) {
            throw BusinessException.forbidden("Only the counterparty agent can reject this exchange");
        }
        requirePending(e);
        e.setStatus(ExchangeStatus.REJECTED);
        e.setResponseNote(responseNote);
        e.setRespondedAt(AppClock.now());
        notificationService.notifyAgent(e.getRequester(), NotificationType.EXCHANGE_UPDATE, RiskLevel.LOW, "Float exchange declined",
                e.getCounterparty().getAgentCode() + " declined exchange " + e.getReference()
                        + (responseNote == null || responseNote.isBlank() ? "" : ": " + responseNote), "FLOAT_EXCHANGE", e.getId());
        ExchangeResponse response = ExchangeResponse.from(e, false);
        publish(e, "EXCHANGE_REJECTED", response);
        return response;
    }

    @Transactional
    public ExchangeResponse cancel(UUID exchangeId, UUID requesterAgentId) {
        FloatExchange e = requireExchange(exchangeId);
        if (!e.getRequester().getId().equals(requesterAgentId)) {
            throw BusinessException.forbidden("Only the requesting agent can cancel this exchange");
        }
        requirePending(e);
        e.setStatus(ExchangeStatus.CANCELLED);
        e.setRespondedAt(AppClock.now());
        ExchangeResponse response = ExchangeResponse.from(e, false);
        publish(e, "EXCHANGE_CANCELLED", response);
        return response;
    }

    public int expireStale() {
        int expired = 0;
        for (UUID id : repository.findExpiredPendingIds(AppClock.now())) {
            try {
                Boolean done = transactionTemplate.execute(status -> {
                    FloatExchange e = requireExchange(id);
                    if (e.getStatus() != ExchangeStatus.PENDING) {
                        return false;
                    }
                    e.setStatus(ExchangeStatus.EXPIRED);
                    e.setRespondedAt(AppClock.now());
                    publish(e, "EXCHANGE_EXPIRED", ExchangeResponse.from(e, false));
                    return true;
                });
                if (Boolean.TRUE.equals(done)) {
                    expired++;
                }
            } catch (RuntimeException ex) {
                log.warn("Could not expire exchange {}: {}", id, ex.getMessage());
            }
        }
        return expired;
    }

    @Transactional(readOnly = true)
    public PageResponse<ExchangeResponse> list(UUID agentId, UUID areaId, ExchangeStatus status, Pageable pageable) {
        Specification<FloatExchange> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (agentId != null) {
                p.add(cb.or(cb.equal(root.get("requester").get("id"), agentId), cb.equal(root.get("counterparty").get("id"), agentId)));
            }
            if (areaId != null) {
                p.add(cb.equal(root.get("requester").get("area").get("id"), areaId));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(repository.findAll(spec, pageable), e -> ExchangeResponse.from(e, false));
    }

    @Transactional(readOnly = true)
    public ExchangeResponse get(UUID exchangeId) {
        return ExchangeResponse.from(requireExchange(exchangeId), false);
    }

    @Transactional(readOnly = true)
    public UUID[] participants(UUID exchangeId) {
        List<Object[]> rows = repository.findParticipants(exchangeId);
        if (rows.isEmpty()) {
            throw BusinessException.notFound("Float exchange", exchangeId);
        }
        return new UUID[]{(UUID) rows.get(0)[0], (UUID) rows.get(0)[1]};
    }

    @Transactional(readOnly = true)
    public List<PeerResponse> peers(UUID agentId) {
        Agent self = requireAgent(agentId);
        return agentRepository.findByArea_IdOrderByAgentCode(self.getArea().getId()).stream()
                .filter(a -> !a.getId().equals(agentId) && a.getStatus().canTransact())
                .map(a -> {
                    BalanceSnapshot b = accounts.agentBalances(a.getId());
                    return new PeerResponse(a.getId(), a.getAgentCode(), a.getBusinessName(), a.getAddress(), a.getLatitude(),
                            a.getLongitude(), GeoUtils.distanceKm(self.getLatitude(), self.getLongitude(), a.getLatitude(), a.getLongitude()),
                            liquidity(b.efloat(), a.getMinEfloatThreshold()), liquidity(b.cash(), a.getMinCashThreshold()));
                })
                .sorted(Comparator.comparing(PeerResponse::distanceKm))
                .toList();
    }

    static String liquidity(BigDecimal balance, BigDecimal threshold) {
        if (balance.compareTo(threshold.multiply(BigDecimal.valueOf(2))) >= 0) {
            return "HIGH";
        }
        return balance.compareTo(threshold) >= 0 ? "MEDIUM" : "LOW";
    }

    private void publish(FloatExchange e, String type, ExchangeResponse response) {
        events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.agent(e.getRequester().getId()), type, response));
        events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.agent(e.getCounterparty().getId()), type, response));
        events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.area(e.getRequester().getArea().getId()), type, response));
    }

    private void requirePending(FloatExchange e) {
        if (e.getStatus() != ExchangeStatus.PENDING) {
            throw BusinessException.conflict("EXCHANGE_NOT_PENDING", "Exchange " + e.getReference() + " is " + e.getStatus());
        }
    }

    private void requireActive(Agent agent) {
        if (!agent.getStatus().canTransact()) {
            throw BusinessException.unprocessable("AGENT_NOT_ACTIVE", "Agent " + agent.getAgentCode() + " is " + agent.getStatus());
        }
    }

    private Agent requireAgent(UUID id) {
        return agentRepository.findWithDetailsById(id).orElseThrow(() -> BusinessException.notFound("Agent", id));
    }

    private FloatExchange requireExchange(UUID id) {
        return repository.findDetailedById(id).orElseThrow(() -> BusinessException.notFound("Float exchange", id));
    }
}
