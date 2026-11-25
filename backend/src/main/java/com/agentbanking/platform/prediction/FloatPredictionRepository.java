package com.agentbanking.platform.prediction;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FloatPredictionRepository extends JpaRepository<FloatPrediction, UUID> {

    @EntityGraph(attributePaths = {"agent"})
    Optional<FloatPrediction> findTopByAgent_IdOrderByCreatedAtDesc(UUID agentId);
}
