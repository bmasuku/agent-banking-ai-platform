package com.agentbanking.platform.credit;

import com.agentbanking.platform.advance.AdvanceStatus;
import com.agentbanking.platform.advance.FloatAdvance;
import com.agentbanking.platform.advance.FloatAdvanceRepository;
import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.agent.AgentRepository;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.fraud.FraudCheckRepository;
import com.agentbanking.platform.fraud.ReviewStatus;
import com.agentbanking.platform.ledger.BalanceSnapshot;
import com.agentbanking.platform.ledger.LedgerAccountService;
import com.agentbanking.platform.risk.InvestigationRepository;
import com.agentbanking.platform.risk.InvestigationStatus;
import com.agentbanking.platform.transaction.FinancialTransactionRepository;
import com.agentbanking.platform.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditFeatureService {

    private static final Set<TransactionType> CUSTOMER_TYPES =
            EnumSet.of(TransactionType.CASH_DEPOSIT, TransactionType.CASH_WITHDRAWAL, TransactionType.AGENT_TRANSFER_TO_CUSTOMER);

    private final AgentRepository agentRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final FloatAdvanceRepository advanceRepository;
    private final FraudCheckRepository fraudCheckRepository;
    private final InvestigationRepository investigationRepository;
    private final LedgerAccountService accounts;

    @Transactional(readOnly = true)
    public CreditFeatures build(UUID agentId, BigDecimal requestedAmount) {
        Agent agent = agentRepository.findById(agentId).orElseThrow(() -> BusinessException.notFound("Agent", agentId));
        Instant now = AppClock.now();
        Instant since = now.minus(Duration.ofDays(30));

        long tenureDays = Math.max(0, Duration.between(agent.getOnboardedAt(), now).toDays());
        long activeDays = transactionRepository.agentActiveDays(agentId, since, now);
        long txCount = transactionRepository.countAgentTransactionsSince(agentId, CUSTOMER_TYPES, since);
        BigDecimal volume = Money.of(transactionRepository.sumAgentAmountSince(agentId, CUSTOMER_TYPES, since));

        List<BigDecimal> dailyCommissions = transactionRepository.agentDailyCommissions(agentId, since, now);
        BigDecimal totalCommission = dailyCommissions.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgDailyCommission = Money.of(totalCommission.divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP));
        double volatility = coefficientOfVariation(dailyCommissions);

        BalanceSnapshot balances = accounts.agentBalances(agentId);
        LocalDate today = AppClock.today();
        List<FloatAdvance> repaid = advanceRepository.findByAgent_IdAndStatus(agentId, AdvanceStatus.REPAID);
        long repaidLate = repaid.stream()
                .filter(a -> a.getDueDate() != null && a.getClosedAt() != null
                        && LocalDate.ofInstant(a.getClosedAt(), AppClock.zone()).isAfter(a.getDueDate()))
                .count();
        List<FloatAdvance> openAdvances = advanceRepository.findByAgentAndStatuses(agentId,
                EnumSet.of(AdvanceStatus.ACTIVE, AdvanceStatus.OVERDUE));
        long overdue = openAdvances.stream().filter(a -> a.getDueDate() != null && a.getDueDate().isBefore(today)).count();
        // Default = more than 30 days past due (open) or closed more than 30 days after the due date.
        long defaultedOpen = openAdvances.stream()
                .filter(a -> a.getDueDate() != null && a.getDueDate().isBefore(today.minusDays(30))).count();
        long defaultedClosed = repaid.stream()
                .filter(a -> a.getDueDate() != null && a.getClosedAt() != null
                        && LocalDate.ofInstant(a.getClosedAt(), AppClock.zone()).isAfter(a.getDueDate().plusDays(30)))
                .count();
        long defaulted = defaultedOpen + defaultedClosed;

        long flagged = fraudCheckRepository.countByAgent_IdAndFlaggedTrueAndCreatedAtAfter(agentId, since);
        long confirmedFraud = fraudCheckRepository.countByAgent_IdAndReviewStatus(agentId, ReviewStatus.CONFIRMED_FRAUD);
        long openInvestigations = investigationRepository.countByAgent_IdAndStatusIn(agentId,
                EnumSet.of(InvestigationStatus.OPEN, InvestigationStatus.IN_PROGRESS, InvestigationStatus.ESCALATED));

        return new CreditFeatures(agentId, agent.getAgentCode(), tenureDays, activeDays, txCount, volume, avgDailyCommission,
                volatility, balances.efloat(), balances.cash(), balances.advancePrincipalOutstanding(),
                balances.advanceInterestOutstanding(), repaid.size(), repaidLate, overdue, defaulted, flagged,
                confirmedFraud, openInvestigations, agent.getStatus().name(),
                requestedAmount == null ? null : Money.of(requestedAmount));
    }

    static double coefficientOfVariation(List<BigDecimal> values) {
        if (values.size() < 2) {
            return 0.0;
        }
        double mean = values.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        if (mean <= 0) {
            return 0.0;
        }
        double variance = values.stream().mapToDouble(v -> Math.pow(v.doubleValue() - mean, 2)).sum() / values.size();
        return Math.sqrt(variance) / mean;
    }
}
