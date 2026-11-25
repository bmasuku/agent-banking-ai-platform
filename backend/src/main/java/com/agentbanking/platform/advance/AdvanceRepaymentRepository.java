package com.agentbanking.platform.advance;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AdvanceRepaymentRepository extends JpaRepository<AdvanceRepayment, UUID> {

    @EntityGraph(attributePaths = {"transaction"})
    List<AdvanceRepayment> findByAdvance_IdOrderByCreatedAtAsc(UUID advanceId);

    @EntityGraph(attributePaths = {"transaction", "advance"})
    List<AdvanceRepayment> findByTransaction_Id(UUID transactionId);

    /** Columns: source, count, sum(principal), sum(interest). */
    @Query("""
            select r.source, count(r), coalesce(sum(r.principalAmount), 0), coalesce(sum(r.interestAmount), 0)
            from AdvanceRepayment r where r.createdAt >= :from and r.createdAt < :to group by r.source
            """)
    List<Object[]> totalsBySource(@Param("from") Instant from, @Param("to") Instant to);
}
