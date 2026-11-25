package com.agentbanking.platform.advance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FloatAdvanceRepository extends JpaRepository<FloatAdvance, UUID>, JpaSpecificationExecutor<FloatAdvance> {

    @Override
    @EntityGraph(attributePaths = {"agent", "agent.user", "agent.area"})
    Page<FloatAdvance> findAll(Specification<FloatAdvance> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"agent", "agent.user", "agent.area"})
    @Query("select a from FloatAdvance a where a.id = :id")
    Optional<FloatAdvance> findDetailedById(@Param("id") UUID id);

    Optional<FloatAdvance> findByIdempotencyKey(String idempotencyKey);

    @Query("select a.agent.id from FloatAdvance a where a.id = :id")
    Optional<UUID> findAgentIdById(@Param("id") UUID id);

    /** Outstanding advances in recovery order: earliest due first. */
    @Query("""
            select a from FloatAdvance a
            where a.agent.id = :agentId and a.status in :statuses
            order by a.dueDate asc, a.disbursedAt asc
            """)
    List<FloatAdvance> findByAgentAndStatuses(@Param("agentId") UUID agentId, @Param("statuses") Collection<AdvanceStatus> statuses);

    long countByAgent_IdAndStatusIn(UUID agentId, Collection<AdvanceStatus> statuses);

    long countByAgent_IdAndStatus(UUID agentId, AdvanceStatus status);

    long countByStatus(AdvanceStatus status);

    @Query("select a.id from FloatAdvance a where a.status in :statuses and a.dueDate < :today")
    List<UUID> findIdsPastDue(@Param("statuses") Collection<AdvanceStatus> statuses, @Param("today") LocalDate today);

    List<FloatAdvance> findByAgent_IdAndStatus(UUID agentId, AdvanceStatus status);

    /** Columns: status, count, sum(principal outstanding), sum(interest outstanding), sum(approved). */
    @Query("""
            select a.status, count(a), coalesce(sum(a.principalOutstanding), 0), coalesce(sum(a.interestOutstanding), 0),
                   coalesce(sum(a.approvedAmount), 0)
            from FloatAdvance a group by a.status
            """)
    List<Object[]> bookSummary();

    @Query("select coalesce(sum(a.requestedAmount), 0) from FloatAdvance a where a.agent.id = :agentId and a.status = com.agentbanking.platform.advance.AdvanceStatus.PENDING")
    BigDecimal pendingAmount(@Param("agentId") UUID agentId);
}
