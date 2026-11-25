package com.agentbanking.platform.reporting;

import com.agentbanking.platform.advance.AdvanceStatus;
import com.agentbanking.platform.advance.RepaymentSource;
import com.agentbanking.platform.agent.AgentDtos.AgentSummary;
import com.agentbanking.platform.exchange.ExchangeStatus;
import com.agentbanking.platform.floatmgmt.ReplenishmentType;
import com.agentbanking.platform.ledger.AccountCategory;
import com.agentbanking.platform.ledger.BalanceType;
import com.agentbanking.platform.transaction.TransactionStatus;
import com.agentbanking.platform.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record Period(Instant from, Instant to) {
    }

    public record TrialBalanceLine(BalanceType balanceType, String label, AccountCategory category, long accountCount,
                                   BigDecimal debit, BigDecimal credit) {
    }

    /** {@code balanced} is true when total debit balances equal total credit balances and journal debits equal credits. */
    public record TrialBalance(Instant asOf, List<TrialBalanceLine> lines, BigDecimal totalDebit, BigDecimal totalCredit,
                               BigDecimal journalDebits, BigDecimal journalCredits, boolean balanced) {
    }

    public record IncomeStatement(Period period, BigDecimal feeIncome, BigDecimal interestIncome, BigDecimal totalRevenue,
                                  BigDecimal commissionExpense, BigDecimal netIncome) {
    }

    public record AreaPosition(UUID areaId, String areaCode, String areaName, BigDecimal efloatPool, BigDecimal cashPool,
                               BigDecimal agentEfloat, BigDecimal agentCash, int agentCount) {
    }

    /**
     * E-money issued (agent e-float + customer wallets + area e-float pools + commission payable) is
     * backed by the settlement bank balance plus advance receivables.
     */
    public record FloatPosition(Instant asOf, BigDecimal settlementBank, BigDecimal centralCashVault, BigDecimal totalAgentEfloat,
                                BigDecimal totalAgentCash, BigDecimal totalCustomerWallets, BigDecimal totalAreaEfloatPools,
                                BigDecimal totalAreaCashPools, BigDecimal totalCommissionPayable, BigDecimal totalAdvanceReceivable,
                                BigDecimal totalEmoneyIssued, List<AreaPosition> areas) {
    }

    public record AdvanceStatusLine(AdvanceStatus status, long count, BigDecimal principalOutstanding,
                                    BigDecimal interestOutstanding, BigDecimal approvedAmount) {
    }

    public record RepaymentSourceLine(RepaymentSource source, long count, BigDecimal principal, BigDecimal interest) {
    }

    public record AdvanceBook(Instant asOf, Period period, List<AdvanceStatusLine> byStatus, BigDecimal totalPrincipalOutstanding,
                              BigDecimal totalInterestOutstanding, List<RepaymentSourceLine> repaymentsInPeriod) {
    }

    public record AgentCommissionLine(UUID agentId, String agentCode, String businessName, long transactions, BigDecimal accrued,
                                      BigDecimal currentPayable) {
    }

    public record CommissionReport(Period period, List<AgentCommissionLine> agents, BigDecimal totalAccrued, long disbursements,
                                   BigDecimal grossDisbursed, BigDecimal principalRecovered, BigDecimal interestRecovered,
                                   BigDecimal netDisbursed) {
    }

    public record TransactionSummaryLine(TransactionType type, TransactionStatus status, long count, BigDecimal amount,
                                         BigDecimal fees, BigDecimal commissions) {
    }

    public record TransactionSummaryReport(Period period, UUID areaId, List<TransactionSummaryLine> lines, long totalCount,
                                           BigDecimal totalAmount, BigDecimal totalFees, BigDecimal totalCommissions) {
    }

    public record ReplenishmentLine(ReplenishmentType type, long count, BigDecimal amount) {
    }

    public record ExchangeLine(ExchangeStatus status, long count, BigDecimal amount) {
    }

    public record AreaReport(Period period, UUID areaId, String areaCode, String areaName, BigDecimal efloatPool, BigDecimal cashPool,
                             List<AgentSummary> agents, TransactionSummaryReport transactions, List<ReplenishmentLine> replenishments,
                             List<ExchangeLine> exchanges, long flaggedTransactions, long agentsLowOnEfloat, long agentsLowOnCash) {
    }
}
