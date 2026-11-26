package com.agentbanking.platform.fieldvisit;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.agent.AgentDtos.AgentBalanceView;
import com.agentbanking.platform.agent.AgentService;
import com.agentbanking.platform.audit.AuditService;
import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.security.AccessControlService;
import com.agentbanking.platform.security.UserPrincipal;
import com.agentbanking.platform.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FieldVisitService {

    private final FieldVisitRepository repository;
    private final AgentService agentService;
    private final AccessControlService access;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public FieldVisitResponse create(UserPrincipal principal, CreateFieldVisitRequest request) {
        Agent agent = agentService.require(request.agentId());
        access.assertAreaManagerOf(principal, agent);

        if (request.clientLocalId() != null && !request.clientLocalId().isBlank()) {
            var existing = repository.findByAreaManager_IdAndClientLocalId(principal.getId(), request.clientLocalId().trim());
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        AgentBalanceView balances = agentService.balances(agent.getId());
        BigDecimal ledgerCash = Money.of(balances.balances().cash());
        BigDecimal verified = request.verifiedCashBalance() == null ? null : Money.of(request.verifiedCashBalance());
        BigDecimal discrepancy = verified == null ? null : verified.subtract(ledgerCash);

        FieldVisit visit = new FieldVisit();
        visit.setAgent(agent);
        visit.setArea(agent.getArea());
        visit.setAreaManager(userRepository.getReferenceById(principal.getId()));
        visit.setVisitType(request.visitType() == null ? VisitType.ROUTINE_AUDIT : request.visitType());
        visit.setNotes(request.notes().trim());
        visit.setLatitude(request.latitude());
        visit.setLongitude(request.longitude());
        visit.setLocationSimulated(Boolean.TRUE.equals(request.locationSimulated()));
        visit.setVerifiedCashBalance(verified);
        visit.setLedgerCashAtVisit(ledgerCash);
        visit.setDiscrepancyAmount(discrepancy);
        visit.setClientLocalId(blankToNull(request.clientLocalId()));
        if (request.visitedAt() != null) {
            visit.setVisitedAt(request.visitedAt());
        }

        FieldVisit saved = repository.save(visit);
        auditService.record("FIELD_VISIT_CREATED", "FIELD_VISIT", saved.getId(), Map.of(
                "agentId", agent.getId(),
                "agentCode", agent.getAgentCode(),
                "visitType", saved.getVisitType().name(),
                "simulated", saved.isLocationSimulated()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<FieldVisitResponse> list(UUID areaId, UUID agentId, Instant from, Instant to, Pageable pageable) {
        Specification<FieldVisit> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("area").get("id"), areaId));
            if (agentId != null) {
                predicates.add(cb.equal(root.get("agent").get("id"), agentId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("visitedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("visitedAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.of(repository.findAll(spec, pageable), FieldVisitService::toResponse);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static FieldVisitResponse toResponse(FieldVisit v) {
        return new FieldVisitResponse(
                v.getId(),
                v.getAgent().getId(),
                v.getAgent().getAgentCode(),
                v.getAgent().getBusinessName(),
                v.getArea().getId(),
                v.getAreaManager().getId(),
                v.getAreaManager().getFullName(),
                v.getVisitType(),
                v.getNotes(),
                v.getLatitude(),
                v.getLongitude(),
                v.isLocationSimulated(),
                v.getVerifiedCashBalance(),
                v.getLedgerCashAtVisit(),
                v.getDiscrepancyAmount(),
                v.getClientLocalId(),
                v.getVisitedAt(),
                v.getCreatedAt()
        );
    }

    public record CreateFieldVisitRequest(
            @NotNull UUID agentId,
            VisitType visitType,
            @NotBlank @Size(max = 4000) String notes,
            @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
            Boolean locationSimulated,
            @DecimalMin("0.0") BigDecimal verifiedCashBalance,
            @Size(max = 64) String clientLocalId,
            Instant visitedAt
    ) {
    }

    public record FieldVisitResponse(
            UUID id,
            UUID agentId,
            String agentCode,
            String agentName,
            UUID areaId,
            UUID areaManagerId,
            String areaManagerName,
            VisitType visitType,
            String notes,
            BigDecimal latitude,
            BigDecimal longitude,
            boolean locationSimulated,
            BigDecimal verifiedCashBalance,
            BigDecimal ledgerCashAtVisit,
            BigDecimal discrepancyAmount,
            String clientLocalId,
            Instant visitedAt,
            Instant createdAt
    ) {
    }
}
