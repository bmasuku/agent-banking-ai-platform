package com.agentbanking.platform.agent;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AgentAccountRepository extends JpaRepository<AgentAccount, UUID> {

    Optional<AgentAccount> findByAgent_Id(UUID agentId);

    /** Acquires a row lock (SELECT ... FOR UPDATE). Call first in a transaction, before loading related rows. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select aa from AgentAccount aa where aa.agent.id = :agentId")
    Optional<AgentAccount> lockByAgentId(@Param("agentId") UUID agentId);
}
