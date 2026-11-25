package com.agentbanking.platform.ledger;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID>, JpaSpecificationExecutor<LedgerAccount> {

    Optional<LedgerAccount> findByAgentIdAndBalanceType(UUID agentId, BalanceType balanceType);

    Optional<LedgerAccount> findByAreaIdAndBalanceType(UUID areaId, BalanceType balanceType);

    Optional<LedgerAccount> findByCustomerIdAndBalanceType(UUID customerId, BalanceType balanceType);

    Optional<LedgerAccount> findByOwnerTypeAndBalanceType(OwnerType ownerType, BalanceType balanceType);

    List<LedgerAccount> findByAgentId(UUID agentId);

    List<LedgerAccount> findByAreaId(UUID areaId);

    List<LedgerAccount> findByOwnerTypeOrderByCode(OwnerType ownerType);

    List<LedgerAccount> findByBalanceTypeAndAgentIdIn(BalanceType balanceType, Collection<UUID> agentIds);

    @Query("""
            select a.balanceType as balanceType, a.category as category, a.normalSide as normalSide,
                   count(a) as accountCount, coalesce(sum(a.balance), 0) as totalBalance
            from LedgerAccount a
            group by a.balanceType, a.category, a.normalSide
            """)
    List<BalanceTypeTotal> totalsByBalanceType();

    @Query("""
            select a from LedgerAccount a
            where a.balanceType = :type and a.balance >= :minimum
            order by a.balance desc
            """)
    List<LedgerAccount> findByBalanceTypeWithMinimum(@Param("type") BalanceType type, @Param("minimum") BigDecimal minimum,
                                                     Pageable pageable);

    interface BalanceTypeTotal {
        BalanceType getBalanceType();

        AccountCategory getCategory();

        EntrySide getNormalSide();

        long getAccountCount();

        BigDecimal getTotalBalance();
    }
}
