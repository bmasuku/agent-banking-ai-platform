package com.agentbanking.platform.commission;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CommissionRepository extends JpaRepository<Commission, UUID>, JpaSpecificationExecutor<Commission> {

    @Override
    @EntityGraph(attributePaths = {"agent", "transaction"})
    Page<Commission> findAll(Specification<Commission> spec, Pageable pageable);

    long countByAgent_IdAndStatus(UUID agentId, CommissionStatus status);

    @Query("select coalesce(sum(c.amount), 0) from Commission c where c.agent.id = :agentId and c.status = :status")
    BigDecimal sumByAgentAndStatus(@Param("agentId") UUID agentId, @Param("status") CommissionStatus status);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("""
            update Commission c set c.status = com.agentbanking.platform.commission.CommissionStatus.DISBURSED,
                   c.disbursement = :disbursement, c.version = c.version + 1
            where c.agent.id = :agentId and c.status = com.agentbanking.platform.commission.CommissionStatus.ACCRUED
            """)
    int markDisbursed(@Param("agentId") UUID agentId, @Param("disbursement") CommissionDisbursement disbursement);

    /** Columns: agent id, count, sum(amount). */
    @Query("""
            select c.agent.id, count(c), coalesce(sum(c.amount), 0) from Commission c
            where c.accruedAt >= :from and c.accruedAt < :to
            group by c.agent.id
            """)
    List<Object[]> totalsByAgent(@Param("from") Instant from, @Param("to") Instant to);
}
