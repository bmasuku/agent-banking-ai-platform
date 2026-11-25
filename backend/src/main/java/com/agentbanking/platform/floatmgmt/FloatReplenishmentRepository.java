package com.agentbanking.platform.floatmgmt;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FloatReplenishmentRepository extends JpaRepository<FloatReplenishment, UUID>,
        JpaSpecificationExecutor<FloatReplenishment> {

    @Override
    @EntityGraph(attributePaths = {"agent", "area", "performedBy", "transaction"})
    Page<FloatReplenishment> findAll(Specification<FloatReplenishment> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"agent", "area", "performedBy", "transaction"})
    Optional<FloatReplenishment> findByTransaction_Id(UUID transactionId);

    /** Columns: type, count, sum(amount). */
    @Query("""
            select r.replenishmentType, count(r), coalesce(sum(r.amount), 0) from FloatReplenishment r
            where r.area.id = :areaId and r.createdAt >= :from and r.createdAt < :to group by r.replenishmentType
            """)
    List<Object[]> totalsForArea(@Param("areaId") UUID areaId, @Param("from") Instant from, @Param("to") Instant to);
}
