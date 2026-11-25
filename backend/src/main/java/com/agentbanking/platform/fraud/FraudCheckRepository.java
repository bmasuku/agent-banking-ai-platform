package com.agentbanking.platform.fraud;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FraudCheckRepository extends JpaRepository<FraudCheck, UUID>, JpaSpecificationExecutor<FraudCheck> {

    Optional<FraudCheck> findByTransaction_Id(UUID transactionId);

    @Override
    @EntityGraph(attributePaths = {"agent", "transaction", "transaction.customer"})
    Page<FraudCheck> findAll(Specification<FraudCheck> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"agent", "agent.area", "transaction", "transaction.customer"})
    @Query("select f from FraudCheck f where f.id = :id")
    Optional<FraudCheck> findDetailedById(@Param("id") UUID id);

    long countByAgent_IdAndFlaggedTrueAndCreatedAtAfter(UUID agentId, Instant after);

    long countByAgent_IdAndReviewStatusIn(UUID agentId, Collection<ReviewStatus> statuses);

    long countByFlaggedTrueAndReviewStatus(ReviewStatus status);

    long countByFlaggedTrueAndCreatedAtAfter(Instant after);

    long countByDecisionAndCreatedAtAfter(FraudDecision decision, Instant after);

    long countByAgent_IdAndReviewStatus(UUID agentId, ReviewStatus status);

    List<FraudCheck> findTop20ByAgent_IdAndFlaggedTrueOrderByCreatedAtDesc(UUID agentId);

    @Query("select count(f) from FraudCheck f where f.flagged = true and f.createdAt >= :from and f.createdAt < :to and f.agent.area.id = :areaId")
    long countFlaggedForArea(@Param("areaId") UUID areaId, @Param("from") Instant from, @Param("to") Instant to);
}
