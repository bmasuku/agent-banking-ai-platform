package com.agentbanking.platform.exchange;

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

public interface FloatExchangeRepository extends JpaRepository<FloatExchange, UUID>, JpaSpecificationExecutor<FloatExchange> {

    @Override
    @EntityGraph(attributePaths = {"requester", "counterparty", "transaction"})
    Page<FloatExchange> findAll(Specification<FloatExchange> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"requester", "requester.area", "requester.user", "counterparty", "counterparty.area",
            "counterparty.user", "transaction"})
    @Query("select e from FloatExchange e where e.id = :id")
    Optional<FloatExchange> findDetailedById(@Param("id") UUID id);

    Optional<FloatExchange> findByIdempotencyKey(String idempotencyKey);

    /** Columns: requester id, counterparty id (nullable for OPEN listings). */
    @Query("select e.requester.id, e.counterparty.id from FloatExchange e where e.id = :id")
    List<Object[]> findParticipants(@Param("id") UUID id);

    @Query("""
            select e.id from FloatExchange e
            where e.status in (
                com.agentbanking.platform.exchange.ExchangeStatus.PENDING,
                com.agentbanking.platform.exchange.ExchangeStatus.OPEN
            ) and e.expiresAt < :now
            """)
    List<UUID> findExpiredPendingIds(@Param("now") Instant now);

    @EntityGraph(attributePaths = {"requester", "requester.area", "counterparty"})
    @Query("""
            select e from FloatExchange e
            where e.status = com.agentbanking.platform.exchange.ExchangeStatus.OPEN
              and e.requester.area.id = :areaId
              and e.expiresAt > :now
            order by e.requestedAt desc
            """)
    List<FloatExchange> findOpenInArea(@Param("areaId") UUID areaId, @Param("now") Instant now);

    /** Columns: status, count, sum(amount). */
    @Query("""
            select e.status, count(e), coalesce(sum(e.amount), 0) from FloatExchange e
            where e.requester.area.id = :areaId and e.requestedAt >= :from and e.requestedAt < :to group by e.status
            """)
    List<Object[]> totalsForArea(@Param("areaId") UUID areaId, @Param("from") Instant from, @Param("to") Instant to);
}
