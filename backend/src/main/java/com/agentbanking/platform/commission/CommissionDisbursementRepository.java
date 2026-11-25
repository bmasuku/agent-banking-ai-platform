package com.agentbanking.platform.commission;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommissionDisbursementRepository extends JpaRepository<CommissionDisbursement, UUID>,
        JpaSpecificationExecutor<CommissionDisbursement> {

    @Override
    @EntityGraph(attributePaths = {"agent", "agent.user", "transaction"})
    Page<CommissionDisbursement> findAll(Specification<CommissionDisbursement> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"agent", "agent.user", "transaction"})
    Optional<CommissionDisbursement> findByTransaction_Id(UUID transactionId);

    @EntityGraph(attributePaths = {"agent", "agent.user", "transaction"})
    Optional<CommissionDisbursement> findTopByAgent_IdOrderByCreatedAtDesc(UUID agentId);

    /** Columns: count, gross, principal recovered, interest recovered, net. */
    @Query("""
            select count(d), coalesce(sum(d.grossCommission), 0), coalesce(sum(d.principalRecovered), 0),
                   coalesce(sum(d.interestRecovered), 0), coalesce(sum(d.netCommission), 0)
            from CommissionDisbursement d where d.createdAt >= :from and d.createdAt < :to
            """)
    List<Object[]> totals(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select coalesce(sum(d.grossCommission), 0) from CommissionDisbursement d where d.agent.id = :agentId")
    BigDecimal lifetimeGross(@Param("agentId") UUID agentId);

    @Query("select coalesce(sum(d.netCommission), 0) from CommissionDisbursement d where d.agent.id = :agentId")
    BigDecimal lifetimeNet(@Param("agentId") UUID agentId);
}
