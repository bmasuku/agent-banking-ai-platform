package com.agentbanking.platform.risk;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.agent.AgentAccount;
import com.agentbanking.platform.agent.AgentAccountRepository;
import com.agentbanking.platform.agent.AgentAccountStatus;
import com.agentbanking.platform.agent.AgentRepository;
import com.agentbanking.platform.agent.AgentStatus;
import com.agentbanking.platform.ai.AiGovernanceFeedbackClient;
import com.agentbanking.platform.audit.AuditService;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.RiskLevel;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.customer.Customer;
import com.agentbanking.platform.fraud.FraudCheck;
import com.agentbanking.platform.fraud.FraudCheckRepository;
import com.agentbanking.platform.fraud.FraudDecision;
import com.agentbanking.platform.fraud.ReviewStatus;
import com.agentbanking.platform.notification.NotificationService;
import com.agentbanking.platform.notification.NotificationType;
import com.agentbanking.platform.risk.RiskDtos.AgentRiskResponse;
import com.agentbanking.platform.risk.RiskDtos.CreateInvestigationRequest;
import com.agentbanking.platform.risk.RiskDtos.FlaggedTransactionResponse;
import com.agentbanking.platform.risk.RiskDtos.FraudCheckDetail;
import com.agentbanking.platform.risk.RiskDtos.InvestigationDetail;
import com.agentbanking.platform.risk.RiskDtos.InvestigationResponse;
import com.agentbanking.platform.risk.RiskDtos.NoteResponse;
import com.agentbanking.platform.risk.RiskDtos.OutcomeRequest;
import com.agentbanking.platform.risk.RiskDtos.RiskDashboard;
import com.agentbanking.platform.user.Role;
import com.agentbanking.platform.user.User;
import com.agentbanking.platform.user.UserRepository;
import com.agentbanking.platform.websocket.RealtimeEvent;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Fraud alert review, investigation case management and agent status controls for risk officers. */
@Service
@RequiredArgsConstructor
public class RiskService {

    private static final Set<InvestigationStatus> OPEN_STATUSES =
            EnumSet.of(InvestigationStatus.OPEN, InvestigationStatus.IN_PROGRESS, InvestigationStatus.ESCALATED);

    private final FraudCheckRepository fraudCheckRepository;
    private final InvestigationRepository investigationRepository;
    private final InvestigationNoteRepository noteRepository;
    private final AgentRepository agentRepository;
    private final AgentAccountRepository agentAccountRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final AiGovernanceFeedbackClient aiGovernanceFeedback;

    public record FlagFilter(ReviewStatus reviewStatus, RiskLevel riskLevel, FraudDecision decision, UUID agentId, UUID areaId,
                             Instant from, Instant to, boolean flaggedOnly) {
    }

    @Transactional(readOnly = true)
    public PageResponse<FlaggedTransactionResponse> flagged(FlagFilter f, Pageable pageable) {
        Specification<FraudCheck> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.flaggedOnly()) {
                p.add(cb.isTrue(root.get("flagged")));
            }
            if (f.reviewStatus() != null) {
                p.add(cb.equal(root.get("reviewStatus"), f.reviewStatus()));
            }
            if (f.riskLevel() != null) {
                p.add(cb.equal(root.get("riskLevel"), f.riskLevel()));
            }
            if (f.decision() != null) {
                p.add(cb.equal(root.get("decision"), f.decision()));
            }
            if (f.agentId() != null) {
                p.add(cb.equal(root.get("agent").get("id"), f.agentId()));
            }
            if (f.areaId() != null) {
                p.add(cb.equal(root.get("agent").get("area").get("id"), f.areaId()));
            }
            if (f.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from()));
            }
            if (f.to() != null) {
                p.add(cb.lessThan(root.get("createdAt"), f.to()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(fraudCheckRepository.findAll(spec, pageable), FlaggedTransactionResponse::from);
    }

    @Transactional(readOnly = true)
    public FraudCheckDetail fraudCheck(UUID id) {
        FraudCheck c = requireCheck(id);
        Specification<Investigation> byCheck = (root, q, cb) -> cb.equal(root.get("fraudCheck").get("id"), id);
        Investigation inv = investigationRepository.findAll(byCheck, Pageable.ofSize(1)).stream().findFirst().orElse(null);
        return new FraudCheckDetail(FlaggedTransactionResponse.from(c), c.getFeatureContributions(), c.getFeatures(),
                c.getModelVersion(), c.getReviewedBy(), c.getReviewNote(), c.getReviewedAt(),
                inv == null ? null : inv.getId(), inv == null ? null : inv.getCaseNumber());
    }

    /** Review outcome for an alert. Choosing UNDER_INVESTIGATION opens a case automatically. */
    @Transactional
    public FraudCheckDetail review(UUID fraudCheckId, ReviewStatus status, String note, UUID reviewerId) {
        if (status == ReviewStatus.NOT_REQUIRED || status == ReviewStatus.PENDING_REVIEW) {
            throw BusinessException.badRequest("INVALID_REVIEW_STATUS", "Review status must be CLEARED, CONFIRMED_FRAUD or UNDER_INVESTIGATION");
        }
        FraudCheck c = requireCheck(fraudCheckId);
        if (!c.isFlagged()) {
            throw BusinessException.unprocessable("NOT_FLAGGED", "This transaction was not flagged for review");
        }
        c.setReviewStatus(status);
        c.setReviewNote(note);
        c.setReviewedBy(reviewerId);
        c.setReviewedAt(AppClock.now());
        if (status == ReviewStatus.UNDER_INVESTIGATION
                && !investigationRepository.existsByFraudCheck_IdAndStatusIn(fraudCheckId, OPEN_STATUSES)) {
            open(c.getAgent(), c, "Investigation of " + c.getTransaction().getReference(),
                    "Opened from fraud alert review: " + note + "\n\nModel explanation: " + c.getExplanation(),
                    c.getRiskLevel() == RiskLevel.LOW ? RiskLevel.MEDIUM : c.getRiskLevel(), reviewerId, reviewerId);
        }
        auditService.record("FRAUD_ALERT_REVIEWED", "FRAUD_CHECK", c.getId(), Map.of("status", status.name(), "note", note,
                "reference", c.getTransaction().getReference()));
        events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.RISK, "FRAUD_ALERT_REVIEWED",
                Map.of("fraudCheckId", c.getId(), "reviewStatus", status)));
        if (status == ReviewStatus.CLEARED || status == ReviewStatus.CONFIRMED_FRAUD) {
            aiGovernanceFeedback.recordDecision(c.getAiPredictionId(), status.name(),
                    reviewerId == null ? "unknown" : reviewerId.toString(), "RISK_OFFICER", note, c.getId().toString());
        }
        return fraudCheck(fraudCheckId);
    }

    @Transactional
    public InvestigationResponse create(CreateInvestigationRequest r, UUID openedBy) {
        Agent agent = agentRepository.findWithDetailsById(r.agentId()).orElseThrow(() -> BusinessException.notFound("Agent", r.agentId()));
        FraudCheck check = null;
        if (r.fraudCheckId() != null) {
            check = requireCheck(r.fraudCheckId());
            if (!check.getAgent().getId().equals(agent.getId())) {
                throw BusinessException.badRequest("FRAUD_CHECK_AGENT_MISMATCH", "The fraud check belongs to a different agent");
            }
            if (check.isFlagged()) {
                check.setReviewStatus(ReviewStatus.UNDER_INVESTIGATION);
            }
        }
        Investigation inv = open(agent, check, r.title(), r.description(), r.severity(), openedBy, r.assigneeId());
        return InvestigationResponse.from(inv);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvestigationResponse> list(InvestigationStatus status, RiskLevel severity, UUID agentId, UUID assigneeId,
                                                    Pageable pageable) {
        Specification<Investigation> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (severity != null) {
                p.add(cb.equal(root.get("severity"), severity));
            }
            if (agentId != null) {
                p.add(cb.equal(root.get("agent").get("id"), agentId));
            }
            if (assigneeId != null) {
                p.add(cb.equal(root.get("assignedTo").get("id"), assigneeId));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(investigationRepository.findAll(spec, pageable), InvestigationResponse::from);
    }

    @Transactional(readOnly = true)
    public InvestigationDetail detail(UUID id) {
        Investigation inv = requireInvestigation(id);
        return new InvestigationDetail(InvestigationResponse.from(inv),
                inv.getFraudCheck() == null ? null : FlaggedTransactionResponse.from(inv.getFraudCheck()),
                noteRepository.findByInvestigation_IdOrderByCreatedAtAsc(id).stream().map(NoteResponse::from).toList());
    }

    @Transactional
    public InvestigationResponse assign(UUID id, UUID assigneeId) {
        Investigation inv = requireInvestigation(id);
        requireOpen(inv);
        User assignee = requireRiskUser(assigneeId);
        inv.setAssignedTo(assignee);
        if (inv.getStatus() == InvestigationStatus.OPEN) {
            inv.setStatus(InvestigationStatus.IN_PROGRESS);
        }
        addSystemNote(inv, "Assigned to " + assignee.getFullName());
        notificationService.notifyUser(assignee, NotificationType.INVESTIGATION_UPDATE, inv.getSeverity(),
                "Investigation assigned: " + inv.getCaseNumber(), inv.getTitle(), "INVESTIGATION", inv.getId());
        auditService.record("INVESTIGATION_ASSIGNED", "INVESTIGATION", inv.getId(), Map.of("assignee", assignee.getUsername()));
        return InvestigationResponse.from(inv);
    }

    @Transactional
    public NoteResponse addNote(UUID id, String note, UUID authorId) {
        Investigation inv = requireInvestigation(id);
        InvestigationNote n = new InvestigationNote();
        n.setInvestigation(inv);
        n.setAuthor(userRepository.getReferenceById(authorId));
        n.setNote(note);
        noteRepository.save(n);
        inv.setUpdatedAt(AppClock.now());
        return NoteResponse.from(n);
    }

    @Transactional
    public InvestigationResponse updateStatus(UUID id, InvestigationStatus status, String note, UUID actorId) {
        if (status == InvestigationStatus.CLOSED) {
            throw BusinessException.badRequest("USE_OUTCOME_ENDPOINT", "Close an investigation by recording its outcome");
        }
        Investigation inv = requireInvestigation(id);
        requireOpen(inv);
        InvestigationStatus previous = inv.getStatus();
        inv.setStatus(status);
        addNote(inv, actorId, "Status changed " + previous + " -> " + status + (note == null || note.isBlank() ? "" : ": " + note));
        auditService.record("INVESTIGATION_STATUS", "INVESTIGATION", inv.getId(), Map.of("from", previous.name(), "to", status.name()));
        publish(inv);
        return InvestigationResponse.from(inv);
    }

    /** Closes the case, records the outcome and applies the chosen remedial actions. */
    @Transactional
    public InvestigationResponse close(UUID id, OutcomeRequest r, UUID actorId) {
        Investigation inv = requireInvestigation(id);
        requireOpen(inv);
        List<InvestigationAction> actions = r.actions() == null ? List.of() : r.actions();
        Agent agent = inv.getAgent();

        for (InvestigationAction action : actions) {
            switch (action) {
                case AGENT_FROZEN -> changeAgentStatus(agent, AgentStatus.FROZEN, "Investigation " + inv.getCaseNumber());
                case AGENT_SUSPENDED -> changeAgentStatus(agent, AgentStatus.SUSPENDED, "Investigation " + inv.getCaseNumber());
                case AGENT_REINSTATED -> changeAgentStatus(agent, AgentStatus.ACTIVE, "Investigation " + inv.getCaseNumber());
                case DAILY_LIMIT_REDUCED -> {
                    AgentAccount account = agentAccountRepository.findByAgent_Id(agent.getId()).orElseThrow();
                    account.setDailyTransactionLimit(Money.of(account.getDailyTransactionLimit().divide(BigDecimal.valueOf(2))));
                }
                case CUSTOMER_BLOCKED -> {
                    Customer customer = inv.getFraudCheck() == null ? null : inv.getFraudCheck().getTransaction().getCustomer();
                    if (customer == null) {
                        throw BusinessException.unprocessable("NO_CUSTOMER", "This investigation is not linked to a customer transaction");
                    }
                    customer.setStatus(Customer.Status.BLOCKED);
                }
                default -> {
                }
            }
        }
        inv.setStatus(InvestigationStatus.CLOSED);
        inv.setOutcome(r.outcome());
        inv.setActionsTaken(actions.stream().map(Enum::name).toList());
        inv.setResolutionNotes(r.resolutionNotes());
        inv.setClosedAt(AppClock.now());
        if (inv.getFraudCheck() != null) {
            ReviewStatus reviewStatus = r.outcome() == InvestigationOutcome.CONFIRMED_FRAUD
                    ? ReviewStatus.CONFIRMED_FRAUD : ReviewStatus.CLEARED;
            inv.getFraudCheck().setReviewStatus(reviewStatus);
            inv.getFraudCheck().setReviewedBy(actorId);
            inv.getFraudCheck().setReviewedAt(AppClock.now());
            inv.getFraudCheck().setReviewNote("Investigation " + inv.getCaseNumber() + " closed: " + r.outcome());
            aiGovernanceFeedback.recordDecision(inv.getFraudCheck().getAiPredictionId(), reviewStatus.name(),
                    actorId == null ? "unknown" : actorId.toString(), "RISK_OFFICER",
                    r.resolutionNotes(), inv.getCaseNumber());
        }
        boolean statusActionTaken = actions.stream().anyMatch(a -> a == InvestigationAction.AGENT_FROZEN
                || a == InvestigationAction.AGENT_SUSPENDED || a == InvestigationAction.AGENT_REINSTATED);
        investigationRepository.flush();
        if (!statusActionTaken && agent.getStatus() == AgentStatus.UNDER_REVIEW
                && investigationRepository.countByAgent_IdAndStatusIn(agent.getId(), OPEN_STATUSES) == 0) {
            changeAgentStatus(agent, AgentStatus.ACTIVE, "No open investigations remain");
        }
        addNote(inv, actorId, "Closed with outcome " + r.outcome() + (actions.isEmpty() ? "" : "; actions " + actions));
        auditService.record("INVESTIGATION_CLOSED", "INVESTIGATION", inv.getId(), Map.of("caseNumber", inv.getCaseNumber(),
                "outcome", r.outcome().name(), "actions", actions.toString()));
        notificationService.notifyAreaManagers(agent.getArea().getId(), NotificationType.INVESTIGATION_UPDATE, inv.getSeverity(),
                "Investigation closed (" + agent.getAgentCode() + ")",
                inv.getCaseNumber() + " closed with outcome " + r.outcome() + ".", "INVESTIGATION", inv.getId());
        publish(inv);
        return InvestigationResponse.from(inv);
    }

    @Transactional
    public AgentRiskResponse setAgentStatus(UUID agentId, AgentStatus status, String reason) {
        Agent agent = agentRepository.findWithDetailsById(agentId).orElseThrow(() -> BusinessException.notFound("Agent", agentId));
        changeAgentStatus(agent, status, reason);
        return toRisk(agent);
    }

    @Transactional(readOnly = true)
    public List<AgentRiskResponse> agentRiskOverview(UUID areaId) {
        List<Agent> agents = areaId == null ? agentRepository.findAllByOrderByAgentCode() : agentRepository.findByArea_IdOrderByAgentCode(areaId);
        return agents.stream().map(this::toRisk)
                .sorted(Comparator.comparing((AgentRiskResponse a) -> a.riskLevel().ordinal()).reversed()
                        .thenComparing(AgentRiskResponse::creditScore))
                .toList();
    }

    @Transactional(readOnly = true)
    public RiskDashboard dashboard() {
        Instant dayAgo = AppClock.now().minus(Duration.ofDays(1));
        Map<String, Long> bySeverity = new LinkedHashMap<>();
        for (RiskLevel level : RiskLevel.values()) {
            bySeverity.put(level.name(), 0L);
        }
        for (Object[] row : investigationRepository.countByStatusAndSeverity()) {
            if (OPEN_STATUSES.contains((InvestigationStatus) row[0])) {
                bySeverity.merge(((RiskLevel) row[1]).name(), ((Number) row[2]).longValue(), Long::sum);
            }
        }
        List<AgentRiskResponse> agents = agentRiskOverview(null);
        return new RiskDashboard(fraudCheckRepository.countByFlaggedTrueAndReviewStatus(ReviewStatus.PENDING_REVIEW),
                investigationRepository.countByStatusIn(OPEN_STATUSES), bySeverity,
                fraudCheckRepository.countByFlaggedTrueAndCreatedAtAfter(dayAgo),
                fraudCheckRepository.countByDecisionAndCreatedAtAfter(FraudDecision.BLOCK, dayAgo),
                agents.stream().limit(5).toList());
    }

    /** Opens a case, puts an active agent under review and notifies the risk team. */
    @Transactional
    public Investigation open(Agent agent, FraudCheck check, String title, String description, RiskLevel severity,
                              UUID openedBy, UUID assigneeId) {
        Investigation inv = new Investigation();
        inv.setCaseNumber(String.format("INV-%d-%05d", AppClock.today().getYear(), investigationRepository.nextCaseSequence()));
        inv.setAgent(agent);
        inv.setFraudCheck(check);
        inv.setTitle(title);
        inv.setDescription(description);
        inv.setSeverity(severity);
        inv.setOpenedBy(openedBy == null ? null : userRepository.getReferenceById(openedBy));
        if (assigneeId != null) {
            inv.setAssignedTo(requireRiskUser(assigneeId));
            inv.setStatus(InvestigationStatus.IN_PROGRESS);
        }
        investigationRepository.save(inv);
        if (agent.getStatus() == AgentStatus.ACTIVE) {
            changeAgentStatus(agent, AgentStatus.UNDER_REVIEW, "Investigation " + inv.getCaseNumber() + " opened");
        }
        auditService.record("INVESTIGATION_OPENED", "INVESTIGATION", inv.getId(), Map.of("caseNumber", inv.getCaseNumber(),
                "agentCode", agent.getAgentCode(), "severity", severity.name()));
        notificationService.notifyRole(Role.RISK_OFFICER, NotificationType.INVESTIGATION_UPDATE, severity,
                "Investigation opened: " + inv.getCaseNumber(), agent.getAgentCode() + " - " + title, "INVESTIGATION", inv.getId());
        publish(inv);
        return inv;
    }

    private void changeAgentStatus(Agent agent, AgentStatus status, String reason) {
        AgentStatus previous = agent.getStatus();
        if (previous == status) {
            return;
        }
        agent.setStatus(status);
        agentAccountRepository.findByAgent_Id(agent.getId()).ifPresent(account -> account.setStatus(
                status == AgentStatus.FROZEN || status == AgentStatus.SUSPENDED ? AgentAccountStatus.FROZEN : AgentAccountStatus.ACTIVE));
        auditService.record("AGENT_STATUS_CHANGED", "AGENT", agent.getId(), Map.of("agentCode", agent.getAgentCode(),
                "from", previous.name(), "to", status.name(), "reason", reason));
        RiskLevel severity = status.canTransact() ? RiskLevel.MEDIUM : RiskLevel.HIGH;
        String message = "Agent status changed from " + previous + " to " + status + ": " + reason;
        notificationService.notifyAgent(agent, NotificationType.ACCOUNT_STATUS, severity, "Account status: " + status, message,
                "AGENT", agent.getId());
        notificationService.notifyAreaManagers(agent.getArea().getId(), NotificationType.ACCOUNT_STATUS, severity,
                agent.getAgentCode() + " is now " + status, message, "AGENT", agent.getId());
        Map<String, Object> payload = Map.of("agentId", agent.getId(), "agentCode", agent.getAgentCode(), "status", status,
                "previousStatus", previous, "reason", reason);
        events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.agent(agent.getId()), "AGENT_STATUS_CHANGED", payload));
        events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.area(agent.getArea().getId()), "AGENT_STATUS_CHANGED", payload));
        events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.RISK, "AGENT_STATUS_CHANGED", payload));
    }

    private AgentRiskResponse toRisk(Agent a) {
        Instant since = AppClock.now().minus(Duration.ofDays(30));
        return new AgentRiskResponse(a.getId(), a.getAgentCode(), a.getBusinessName(), a.getArea().getCode(), a.getStatus(),
                a.getRiskLevel(), a.getCreditScore(), fraudCheckRepository.countByAgent_IdAndFlaggedTrueAndCreatedAtAfter(a.getId(), since),
                fraudCheckRepository.countByAgent_IdAndReviewStatus(a.getId(), ReviewStatus.PENDING_REVIEW),
                investigationRepository.countByAgent_IdAndStatusIn(a.getId(), OPEN_STATUSES));
    }

    private void addSystemNote(Investigation inv, String text) {
        InvestigationNote n = new InvestigationNote();
        n.setInvestigation(inv);
        n.setNote(text);
        noteRepository.save(n);
    }

    private void addNote(Investigation inv, UUID authorId, String text) {
        InvestigationNote n = new InvestigationNote();
        n.setInvestigation(inv);
        n.setAuthor(authorId == null ? null : userRepository.getReferenceById(authorId));
        n.setNote(text);
        noteRepository.save(n);
    }

    private void publish(Investigation inv) {
        events.publishEvent(RealtimeEvent.topic(RealtimeEvent.Topics.RISK, "INVESTIGATION_UPDATED", Map.of(
                "investigationId", inv.getId(), "caseNumber", inv.getCaseNumber(), "status", inv.getStatus(),
                "agentCode", inv.getAgent().getAgentCode())));
    }

    private User requireRiskUser(UUID id) {
        User user = userRepository.findById(id).orElseThrow(() -> BusinessException.notFound("User", id));
        if (user.getRole() != Role.RISK_OFFICER && user.getRole() != Role.ADMIN) {
            throw BusinessException.badRequest("INVALID_ASSIGNEE", "Investigations can only be assigned to risk officers or administrators");
        }
        return user;
    }

    private void requireOpen(Investigation inv) {
        if (inv.getStatus() == InvestigationStatus.CLOSED) {
            throw BusinessException.conflict("INVESTIGATION_CLOSED", "Investigation " + inv.getCaseNumber() + " is closed");
        }
    }

    private FraudCheck requireCheck(UUID id) {
        return fraudCheckRepository.findDetailedById(id).orElseThrow(() -> BusinessException.notFound("Fraud check", id));
    }

    private Investigation requireInvestigation(UUID id) {
        return investigationRepository.findDetailedById(id).orElseThrow(() -> BusinessException.notFound("Investigation", id));
    }
}
