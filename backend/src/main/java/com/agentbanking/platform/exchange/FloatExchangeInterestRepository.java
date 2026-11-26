package com.agentbanking.platform.exchange;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FloatExchangeInterestRepository extends JpaRepository<FloatExchangeInterest, UUID> {

    @EntityGraph(attributePaths = {"interestedAgent", "exchange"})
    List<FloatExchangeInterest> findByExchange_IdOrderByCreatedAtAsc(UUID exchangeId);

    Optional<FloatExchangeInterest> findByExchange_IdAndInterestedAgent_Id(UUID exchangeId, UUID agentId);

    @EntityGraph(attributePaths = {"interestedAgent", "interestedAgent.area", "exchange"})
    @Query("select i from FloatExchangeInterest i where i.id = :id")
    Optional<FloatExchangeInterest> findDetailedById(@Param("id") UUID id);
}
