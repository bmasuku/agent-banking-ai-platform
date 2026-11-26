package com.agentbanking.platform.exchange;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FloatExchangeMessageRepository extends JpaRepository<FloatExchangeMessage, UUID> {

    @EntityGraph(attributePaths = {"sender"})
    List<FloatExchangeMessage> findByExchange_IdOrderByCreatedAtAsc(UUID exchangeId);
}
