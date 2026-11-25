package com.agentbanking.platform.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, UUID>,
        JpaSpecificationExecutor<FinancialTransaction> {

    Optional<FinancialTransaction> findByIdempotencyKey(String idempotencyKey);

    @Override
    @EntityGraph(attributePaths = {"agent", "customer", "counterpartyAgent"})
    Page<FinancialTransaction> findAll(Specification<FinancialTransaction> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"agent", "customer", "counterpartyAgent"})
    @Query("select t from FinancialTransaction t where t.id = :id")
    Optional<FinancialTransaction> findDetailedById(@Param("id") UUID id);

    @Query("""
            select count(t) from FinancialTransaction t
            where t.agent.id = :agentId and t.status = com.agentbanking.platform.transaction.TransactionStatus.COMPLETED
              and t.type in :types and t.createdAt >= :since
            """)
    long countAgentTransactionsSince(@Param("agentId") UUID agentId, @Param("types") Collection<TransactionType> types,
                                     @Param("since") Instant since);

    @Query("""
            select coalesce(sum(t.amount), 0) from FinancialTransaction t
            where t.agent.id = :agentId and t.status = com.agentbanking.platform.transaction.TransactionStatus.COMPLETED
              and t.type in :types and t.createdAt >= :since
            """)
    BigDecimal sumAgentAmountSince(@Param("agentId") UUID agentId, @Param("types") Collection<TransactionType> types,
                                   @Param("since") Instant since);

    @Query("""
            select coalesce(sum(t.commissionAmount), 0) from FinancialTransaction t
            where t.agent.id = :agentId and t.status = com.agentbanking.platform.transaction.TransactionStatus.COMPLETED
              and t.createdAt >= :since
            """)
    BigDecimal sumAgentCommissionSince(@Param("agentId") UUID agentId, @Param("since") Instant since);

    @Query("""
            select count(t) from FinancialTransaction t
            where t.agent.id = :agentId and t.type in :types and t.createdAt >= :since
              and t.amount >= :lower and t.amount < :upper
            """)
    long countAgentAmountBandSince(@Param("agentId") UUID agentId, @Param("types") Collection<TransactionType> types,
                                   @Param("since") Instant since, @Param("lower") BigDecimal lower, @Param("upper") BigDecimal upper);

    @Query("""
            select count(t) from FinancialTransaction t
            where t.customer.id = :customerId and t.createdAt >= :since and t.type in :types
            """)
    long countCustomerTransactionsSince(@Param("customerId") UUID customerId, @Param("types") Collection<TransactionType> types,
                                        @Param("since") Instant since);

    @Query("""
            select count(distinct t.agent.id) from FinancialTransaction t
            where t.customer.id = :customerId and t.createdAt >= :since and t.type in :types
            """)
    long countCustomerDistinctAgentsSince(@Param("customerId") UUID customerId, @Param("types") Collection<TransactionType> types,
                                          @Param("since") Instant since);

    /** Columns: count, average amount, population std-dev of amount. */
    @Query(value = """
            select count(*), coalesce(avg(amount), 0), coalesce(stddev_pop(amount), 0)
            from financial_transactions
            where agent_id = :agentId and status = 'COMPLETED'
              and type in ('CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'AGENT_TRANSFER_TO_CUSTOMER')
              and created_at >= :since and created_at < :until
            """, nativeQuery = true)
    List<Object[]> agentAmountStats(@Param("agentId") UUID agentId, @Param("since") Instant since, @Param("until") Instant until);

    /** Columns: distinct active days in the window. */
    @Query(value = """
            select count(distinct date_trunc('day', created_at))
            from financial_transactions
            where agent_id = :agentId and status = 'COMPLETED'
              and type in ('CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'AGENT_TRANSFER_TO_CUSTOMER')
              and created_at >= :since and created_at < :until
            """, nativeQuery = true)
    long agentActiveDays(@Param("agentId") UUID agentId, @Param("since") Instant since, @Param("until") Instant until);

    /** Daily commission totals for an agent, used to measure income stability. */
    @Query(value = """
            select coalesce(sum(commission_amount), 0)
            from financial_transactions
            where agent_id = :agentId and status = 'COMPLETED' and created_at >= :since and created_at < :until
            group by date_trunc('day', created_at)
            """, nativeQuery = true)
    List<BigDecimal> agentDailyCommissions(@Param("agentId") UUID agentId, @Param("since") Instant since, @Param("until") Instant until);

    /** Columns: type, status, count, sum(amount), sum(fee), sum(commission). */
    @Query("""
            select t.type, t.status, count(t), coalesce(sum(t.amount), 0), coalesce(sum(t.feeAmount), 0), coalesce(sum(t.commissionAmount), 0)
            from FinancialTransaction t
            where t.createdAt >= :from and t.createdAt < :to
            group by t.type, t.status
            """)
    List<Object[]> summarize(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select t.type, t.status, count(t), coalesce(sum(t.amount), 0), coalesce(sum(t.feeAmount), 0), coalesce(sum(t.commissionAmount), 0)
            from FinancialTransaction t
            where t.createdAt >= :from and t.createdAt < :to and t.areaId = :areaId
            group by t.type, t.status
            """)
    List<Object[]> summarizeForArea(@Param("from") Instant from, @Param("to") Instant to, @Param("areaId") UUID areaId);

    @Query("""
            select t from FinancialTransaction t
            where t.agent.id = :agentId and t.createdAt >= :since and t.createdAt <= :until
              and t.type in :types
            order by t.createdAt asc
            """)
    List<FinancialTransaction> findAgentTransactionsBetween(@Param("agentId") UUID agentId, @Param("since") Instant since,
                                                            @Param("until") Instant until, @Param("types") Collection<TransactionType> types);

    /**
     * Peer anomaly features per agent in an area for the last 7 days. Columns: agent_id, agent_code,
     * transactions_7d, volume_7d, off_hours_7d, withdrawals_7d, reversals_7d, distinct_customers_7d.
     */
    @Query(value = """
            select a.id,
                   a.agent_code,
                   count(t.id) filter (where t.status = 'COMPLETED'
                     and t.type in ('CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'AGENT_TRANSFER_TO_CUSTOMER')) as transactions_7d,
                   coalesce(sum(t.amount) filter (where t.status = 'COMPLETED'
                     and t.type in ('CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'AGENT_TRANSFER_TO_CUSTOMER')), 0) as volume_7d,
                   count(t.id) filter (where t.status = 'COMPLETED'
                     and t.type in ('CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'AGENT_TRANSFER_TO_CUSTOMER')
                     and (extract(hour from t.created_at at time zone 'Africa/Harare') < 6
                          or extract(hour from t.created_at at time zone 'Africa/Harare') >= 22)) as off_hours_7d,
                   count(t.id) filter (where t.status = 'COMPLETED' and t.type = 'CASH_WITHDRAWAL') as withdrawals_7d,
                   count(t.id) filter (where t.status = 'REVERSED'
                     and t.type in ('CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'AGENT_TRANSFER_TO_CUSTOMER')) as reversals_7d,
                   count(distinct t.customer_id) filter (where t.status = 'COMPLETED'
                     and t.type in ('CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'AGENT_TRANSFER_TO_CUSTOMER')) as distinct_customers_7d,
                   (select count(*) from fraud_checks f where f.agent_id = a.id and f.flagged = true
                      and f.created_at >= :since and f.created_at < :until) as flagged_7d
            from agents a
            left join financial_transactions t on t.agent_id = a.id and t.created_at >= :since and t.created_at < :until
            where a.area_id = :areaId
            group by a.id, a.agent_code
            order by a.agent_code
            """, nativeQuery = true)
    List<Object[]> peerAnomalyFeatures(@Param("areaId") UUID areaId, @Param("since") Instant since, @Param("until") Instant until);
}
