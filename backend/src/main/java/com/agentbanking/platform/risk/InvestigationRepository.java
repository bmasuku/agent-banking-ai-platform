package com.agentbanking.platform.risk;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvestigationRepository extends JpaRepository<Investigation, UUID>, JpaSpecificationExecutor<Investigation> {

    @Override
    @EntityGraph(attributePaths = {"agent", "assignedTo", "openedBy"})
    Page<Investigation> findAll(Specification<Investigation> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"agent", "agent.area", "agent.user", "assignedTo", "openedBy", "fraudCheck", "fraudCheck.transaction"})
    @Query("select i from Investigation i where i.id = :id")
    Optional<Investigation> findDetailedById(@Param("id") UUID id);

    long countByAgent_IdAndStatusIn(UUID agentId, Collection<InvestigationStatus> statuses);

    long countByStatusIn(Collection<InvestigationStatus> statuses);

    boolean existsByFraudCheck_IdAndStatusIn(UUID fraudCheckId, Collection<InvestigationStatus> statuses);

    @Query(value = "select nextval('investigation_case_seq')", nativeQuery = true)
    long nextCaseSequence();

    /** Columns: status, severity, count. */
    @Query("select i.status, i.severity, count(i) from Investigation i group by i.status, i.severity")
    List<Object[]> countByStatusAndSeverity();
}
