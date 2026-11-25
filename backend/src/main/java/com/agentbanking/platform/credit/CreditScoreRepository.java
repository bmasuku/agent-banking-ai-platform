package com.agentbanking.platform.credit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CreditScoreRepository extends JpaRepository<CreditScore, UUID> {

    Optional<CreditScore> findTopByAgent_IdOrderByCreatedAtDesc(UUID agentId);

    Page<CreditScore> findByAgent_IdOrderByCreatedAtDesc(UUID agentId, Pageable pageable);
}
