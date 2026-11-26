package com.agentbanking.platform.fieldvisit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface FieldVisitRepository extends JpaRepository<FieldVisit, UUID>, JpaSpecificationExecutor<FieldVisit> {

    Optional<FieldVisit> findByAreaManager_IdAndClientLocalId(UUID areaManagerId, String clientLocalId);
}
