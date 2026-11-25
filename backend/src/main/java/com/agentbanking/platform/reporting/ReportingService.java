package com.agentbanking.platform.reporting;

import com.agentbanking.platform.advance.AdvanceRepaymentRepository;
import com.agentbanking.platform.advance.AdvanceStatus;
import com.agentbanking.platform.advance.FloatAdvanceRepository;
import com.agentbanking.platform.advance.RepaymentSource;
import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.agent.AgentDtos.AgentSummary;
import com.agentbanking.platform.agent.AgentRepository;
import com.agentbanking.platform.agent.AgentService;
import com.agentbanking.platform.area.Area;
import com.agentbanking.platform.area.AreaRepository;
import com.agentbanking.platform.commission.CommissionDisbursementRepository;
import com.agentbanking.platform.commission.CommissionRepository;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.exchange.ExchangeStatus;
import com.agentbanking.platform.exchange.FloatExchangeRepository;
import com.agentbanking.platform.floatmgmt.FloatReplenishmentRepository;
import com.agentbanking.platform.floatmgmt.ReplenishmentType;
import com.agentbanking.platform.fraud.FraudCheckRepository;
import com.agentbanking.platform.ledger.BalanceType;
import com.agentbanking.platform.ledger.EntrySide;
import com.agentbanking.platform.ledger.LedgerAccount;
import com.agentbanking.platform.ledger.LedgerAccountRepository;
import com.agentbanking.platform.ledger.LedgerAccountService;
import com.agentbanking.platform.ledger.LedgerEntryRepository;
import com.agentbanking.platform.reporting.ReportDtos.AdvanceBook;
import com.agentbanking.platform.reporting.ReportDtos.AdvanceStatusLine;
import com.agentbanking.platform.reporting.ReportDtos.AgentCommissionLine;
import com.agentbanking.platform.reporting.ReportDtos.AreaPosition;
import com.agentbanking.platform.reporting.ReportDtos.AreaReport;
import com.agentbanking.platform.reporting.ReportDtos.CommissionReport;
import com.agentbanking.platform.reporting.ReportDtos.ExchangeLine;
import com.agentbanking.platform.reporting.ReportDtos.FloatPosition;
import com.agentbanking.platform.reporting.ReportDtos.IncomeStatement;
import com.agentbanking.platform.reporting.ReportDtos.Period;
import com.agentbanking.platform.reporting.ReportDtos.ReplenishmentLine;
import com.agentbanking.platform.reporting.ReportDtos.RepaymentSourceLine;
import com.agentbanking.platform.reporting.ReportDtos.TransactionSummaryLine;
import com.agentbanking.platform.reporting.ReportDtos.TransactionSummaryReport;
import com.agentbanking.platform.reporting.ReportDtos.TrialBalance;
import com.agentbanking.platform.reporting.ReportDtos.TrialBalanceLine;
import com.agentbanking.platform.transaction.FinancialTransactionRepository;
import com.agentbanking.platform.transaction.TransactionStatus;
import com.agentbanking.platform.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Financial and operational reports computed from the ledger and operational records. */
@Service
@RequiredArgsConstructor
public class ReportingService {

    private final LedgerAccountRepository accountRepository;
    private final LedgerEntryRepository entryRepository;
    private final LedgerAccountService accounts;
    private final FinancialTransactionRepository transactionRepository;
    private final FloatAdvanceRepository advanceRepository;
    private final AdvanceRepaymentRepository repaymentRepository;
    private final CommissionRepository commissionRepository;
    private final CommissionDisbursementRepository disbursementRepository;
    private final FloatReplenishmentRepository replenishmentRepository;
    private final FloatExchangeRepository exchangeRepository;
    private final FraudCheckRepository fraudCheckRepository;
    private final AgentRepository agentRepository;
    private final AreaRepository areaRepository;
    private final AgentService agentService;

    /** Default reporting window: the last 30 days up to now. */
    public static Period period(Instant from, Instant to) {
        Instant end = to != null ? to : AppClock.now();
        Instant start = from != null ? from : end.minus(Duration.ofDays(30));
        if (!start.isBefore(end)) {
            throw BusinessException.badRequest("INVALID_PERIOD", "'from' must be before 'to'");
        }
        return new Period(start, end);
    }

    @Transactional(readOnly = true)
    public TrialBalance trialBalance() {
        List<TrialBalanceLine> lines = new ArrayList<>();
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (LedgerAccountRepository.BalanceTypeTotal t : accountRepository.totalsByBalanceType()) {
            BigDecimal balance = Money.of(t.getTotalBalance());
            boolean debitNormal = t.getNormalSide() == EntrySide.DEBIT;
            BigDecimal debit = (debitNormal ? balance : balance.negate()).max(Money.ZERO);
            BigDecimal credit = (debitNormal ? balance.negate() : balance).max(Money.ZERO);
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
            lines.add(new TrialBalanceLine(t.getBalanceType(), t.getBalanceType().label(), t.getCategory(), t.getAccountCount(),
                    debit, credit));
        }
        lines.sort(Comparator.comparing(l -> l.balanceType().ordinal()));
        BigDecimal journalDebits = Money.of(entryRepository.totalDebits());
        BigDecimal journalCredits = Money.of(entryRepository.totalCredits());
        return new TrialBalance(AppClock.now(), lines, Money.of(totalDebit), Money.of(totalCredit), journalDebits, journalCredits,
                totalDebit.compareTo(totalCredit) == 0 && journalDebits.compareTo(journalCredits) == 0);
    }

    @Transactional(readOnly = true)
    public IncomeStatement incomeStatement(Instant from, Instant to) {
        Period p = period(from, to);
        Map<BalanceType, BigDecimal[]> sums = new EnumMap<>(BalanceType.class);
        for (LedgerEntryRepository.PeriodMovement m : entryRepository.periodMovements(
                List.of(BalanceType.FEE_INCOME, BalanceType.INTEREST_INCOME, BalanceType.COMMISSION_EXPENSE), p.from(), p.to())) {
            BigDecimal[] dc = sums.computeIfAbsent(m.getBalanceType(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            dc[m.getSide() == EntrySide.DEBIT ? 0 : 1] = dc[m.getSide() == EntrySide.DEBIT ? 0 : 1].add(m.getTotal());
        }
        BigDecimal fee = credits(sums, BalanceType.FEE_INCOME);
        BigDecimal interest = credits(sums, BalanceType.INTEREST_INCOME);
        BigDecimal[] exp = sums.getOrDefault(BalanceType.COMMISSION_EXPENSE, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        BigDecimal commission = Money.of(exp[0].subtract(exp[1]));
        BigDecimal revenue = fee.add(interest);
        return new IncomeStatement(p, fee, interest, revenue, commission, Money.of(revenue.subtract(commission)));
    }

    @Transactional(readOnly = true)
    public FloatPosition floatPosition() {
        Map<BalanceType, BigDecimal> totals = new EnumMap<>(BalanceType.class);
        accountRepository.totalsByBalanceType().forEach(t -> totals.put(t.getBalanceType(), Money.of(t.getTotalBalance())));
        List<AreaPosition> areas = new ArrayList<>();
        for (Area area : areaRepository.findAllByOrderByName()) {
            List<Agent> agents = agentRepository.findByArea_IdOrderByAgentCode(area.getId());
            List<UUID> ids = agents.stream().map(Agent::getId).toList();
            BigDecimal efloat = ids.isEmpty() ? Money.ZERO : sum(accountRepository.findByBalanceTypeAndAgentIdIn(BalanceType.EFLOAT, ids));
            BigDecimal cash = ids.isEmpty() ? Money.ZERO : sum(accountRepository.findByBalanceTypeAndAgentIdIn(BalanceType.CASH, ids));
            areas.add(new AreaPosition(area.getId(), area.getCode(), area.getName(),
                    accounts.areaBalance(area.getId(), BalanceType.AREA_EFLOAT_POOL),
                    accounts.areaBalance(area.getId(), BalanceType.AREA_CASH_POOL), efloat, cash, agents.size()));
        }
        BigDecimal agentEfloat = get(totals, BalanceType.EFLOAT);
        BigDecimal wallets = get(totals, BalanceType.CUSTOMER_WALLET);
        BigDecimal pools = get(totals, BalanceType.AREA_EFLOAT_POOL);
        BigDecimal payable = get(totals, BalanceType.COMMISSION_PAYABLE);
        return new FloatPosition(AppClock.now(), get(totals, BalanceType.SETTLEMENT_BANK), get(totals, BalanceType.CENTRAL_CASH_VAULT),
                agentEfloat, get(totals, BalanceType.CASH), wallets, pools, get(totals, BalanceType.AREA_CASH_POOL), payable,
                Money.of(get(totals, BalanceType.ADVANCE_PRINCIPAL).add(get(totals, BalanceType.ADVANCE_INTEREST))),
                Money.of(agentEfloat.add(wallets).add(pools).add(payable)), areas);
    }

    @Transactional(readOnly = true)
    public AdvanceBook advanceBook(Instant from, Instant to) {
        Period p = period(from, to);
        List<AdvanceStatusLine> byStatus = new ArrayList<>();
        BigDecimal principal = BigDecimal.ZERO;
        BigDecimal interest = BigDecimal.ZERO;
        for (Object[] row : advanceRepository.bookSummary()) {
            AdvanceStatusLine line = new AdvanceStatusLine((AdvanceStatus) row[0], ((Number) row[1]).longValue(),
                    Money.of((BigDecimal) row[2]), Money.of((BigDecimal) row[3]), Money.of((BigDecimal) row[4]));
            byStatus.add(line);
            principal = principal.add(line.principalOutstanding());
            interest = interest.add(line.interestOutstanding());
        }
        byStatus.sort(Comparator.comparing(l -> l.status().ordinal()));
        List<RepaymentSourceLine> repayments = repaymentRepository.totalsBySource(p.from(), p.to()).stream()
                .map(r -> new RepaymentSourceLine((RepaymentSource) r[0], ((Number) r[1]).longValue(), Money.of((BigDecimal) r[2]),
                        Money.of((BigDecimal) r[3])))
                .toList();
        return new AdvanceBook(AppClock.now(), p, byStatus, Money.of(principal), Money.of(interest), repayments);
    }

    @Transactional(readOnly = true)
    public CommissionReport commissionReport(Instant from, Instant to) {
        Period p = period(from, to);
        Map<UUID, Object[]> accrued = new HashMap<>();
        commissionRepository.totalsByAgent(p.from(), p.to()).forEach(r -> accrued.put((UUID) r[0], r));
        List<AgentCommissionLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Agent a : agentRepository.findAllByOrderByAgentCode()) {
            Object[] r = accrued.get(a.getId());
            long count = r == null ? 0 : ((Number) r[1]).longValue();
            BigDecimal amount = r == null ? Money.ZERO : Money.of((BigDecimal) r[2]);
            total = total.add(amount);
            lines.add(new AgentCommissionLine(a.getId(), a.getAgentCode(), a.getBusinessName(), count, amount,
                    accounts.agentBalances(a.getId()).commissionPayable()));
        }
        Object[] d = disbursementRepository.totals(p.from(), p.to()).get(0);
        return new CommissionReport(p, lines, Money.of(total), ((Number) d[0]).longValue(), Money.of((BigDecimal) d[1]),
                Money.of((BigDecimal) d[2]), Money.of((BigDecimal) d[3]), Money.of((BigDecimal) d[4]));
    }

    @Transactional(readOnly = true)
    public TransactionSummaryReport transactionSummary(Instant from, Instant to, UUID areaId) {
        Period p = period(from, to);
        List<Object[]> rows = areaId == null ? transactionRepository.summarize(p.from(), p.to())
                : transactionRepository.summarizeForArea(p.from(), p.to(), areaId);
        List<TransactionSummaryLine> lines = new ArrayList<>();
        long count = 0;
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal fees = BigDecimal.ZERO;
        BigDecimal commissions = BigDecimal.ZERO;
        for (Object[] r : rows) {
            TransactionSummaryLine line = new TransactionSummaryLine((TransactionType) r[0], (TransactionStatus) r[1],
                    ((Number) r[2]).longValue(), Money.of((BigDecimal) r[3]), Money.of((BigDecimal) r[4]), Money.of((BigDecimal) r[5]));
            lines.add(line);
            if (line.status() == TransactionStatus.COMPLETED) {
                count += line.count();
                amount = amount.add(line.amount());
                fees = fees.add(line.fees());
                commissions = commissions.add(line.commissions());
            }
        }
        lines.sort(Comparator.comparing((TransactionSummaryLine l) -> l.type().ordinal()).thenComparing(l -> l.status().ordinal()));
        return new TransactionSummaryReport(p, areaId, lines, count, Money.of(amount), Money.of(fees), Money.of(commissions));
    }

    @Transactional(readOnly = true)
    public AreaReport areaReport(UUID areaId, Instant from, Instant to) {
        Period p = period(from, to);
        Area area = areaRepository.findById(areaId).orElseThrow(() -> BusinessException.notFound("Area", areaId));
        List<AgentSummary> agents = agentService.summaries(areaId);
        List<ReplenishmentLine> replenishments = replenishmentRepository.totalsForArea(areaId, p.from(), p.to()).stream()
                .map(r -> new ReplenishmentLine((ReplenishmentType) r[0], ((Number) r[1]).longValue(), Money.of((BigDecimal) r[2])))
                .toList();
        List<ExchangeLine> exchanges = exchangeRepository.totalsForArea(areaId, p.from(), p.to()).stream()
                .map(r -> new ExchangeLine((ExchangeStatus) r[0], ((Number) r[1]).longValue(), Money.of((BigDecimal) r[2])))
                .toList();
        return new AreaReport(p, areaId, area.getCode(), area.getName(),
                accounts.areaBalance(areaId, BalanceType.AREA_EFLOAT_POOL), accounts.areaBalance(areaId, BalanceType.AREA_CASH_POOL),
                agents, transactionSummary(p.from(), p.to(), areaId), replenishments, exchanges,
                fraudCheckRepository.countFlaggedForArea(areaId, p.from(), p.to()),
                agents.stream().filter(AgentSummary::efloatLow).count(), agents.stream().filter(AgentSummary::cashLow).count());
    }

    private static BigDecimal credits(Map<BalanceType, BigDecimal[]> sums, BalanceType type) {
        BigDecimal[] dc = sums.getOrDefault(type, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        return Money.of(dc[1].subtract(dc[0]));
    }

    private static BigDecimal get(Map<BalanceType, BigDecimal> totals, BalanceType type) {
        return totals.getOrDefault(type, Money.ZERO);
    }

    private static BigDecimal sum(List<LedgerAccount> list) {
        return Money.of(list.stream().map(LedgerAccount::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
