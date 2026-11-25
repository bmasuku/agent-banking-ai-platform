package com.agentbanking.platform.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TariffRuleRepository extends JpaRepository<TariffRule, UUID> {

    List<TariffRule> findByTransactionTypeAndActiveTrueOrderByMinAmount(TransactionType type);

    List<TariffRule> findAllByOrderByTransactionTypeAscMinAmountAsc();
}
