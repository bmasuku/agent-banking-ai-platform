package com.agentbanking.platform.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AgentBalanceRepository extends JpaRepository<AgentBalance, UUID> {
}
