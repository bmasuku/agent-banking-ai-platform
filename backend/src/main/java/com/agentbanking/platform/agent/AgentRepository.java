package com.agentbanking.platform.agent;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentRepository extends JpaRepository<Agent, UUID> {

    Optional<Agent> findByUser_Id(UUID userId);

    @EntityGraph(attributePaths = {"area", "user"})
    Optional<Agent> findByAgentCode(String agentCode);

    @EntityGraph(attributePaths = {"area", "user"})
    List<Agent> findByArea_IdOrderByAgentCode(UUID areaId);

    @EntityGraph(attributePaths = {"area", "user"})
    List<Agent> findAllByOrderByAgentCode();

    @EntityGraph(attributePaths = {"area", "user"})
    @Query("select a from Agent a where a.id = :id")
    Optional<Agent> findWithDetailsById(UUID id);

    long countByArea_Id(UUID areaId);

    @Query("select a.agentCode from Agent a order by a.agentCode desc limit 1")
    Optional<String> findHighestAgentCode();
}
