package com.agentbanking.platform.risk;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InvestigationNoteRepository extends JpaRepository<InvestigationNote, UUID> {

    @EntityGraph(attributePaths = {"author"})
    List<InvestigationNote> findByInvestigation_IdOrderByCreatedAtAsc(UUID investigationId);
}
