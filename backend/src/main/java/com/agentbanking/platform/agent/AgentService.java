package com.agentbanking.platform.agent;

import com.agentbanking.platform.agent.AgentDtos.AgentBalanceView;
import com.agentbanking.platform.agent.AgentDtos.AgentLocation;
import com.agentbanking.platform.agent.AgentDtos.AgentProfile;
import com.agentbanking.platform.agent.AgentDtos.AgentSummary;
import com.agentbanking.platform.agent.AgentDtos.UpdateAgentRequest;
import com.agentbanking.platform.audit.AuditService;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.config.AppProperties;
import com.agentbanking.platform.ledger.BalanceSnapshot;
import com.agentbanking.platform.ledger.BalanceType;
import com.agentbanking.platform.ledger.LedgerAccountService;
import com.agentbanking.platform.ledger.LedgerQueryService;
import com.agentbanking.platform.ledger.LedgerQueryService.StatementLine;
import com.agentbanking.platform.prediction.FloatPrediction;
import com.agentbanking.platform.prediction.FloatPredictionRepository;
import com.agentbanking.platform.transaction.FinancialTransactionRepository;
import com.agentbanking.platform.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read models for agents. Every balance shown is read from the ledger accounts. */
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentRepository agentRepository;
    private final AgentAccountRepository accountRepository;
    private final LedgerAccountService accounts;
    private final LedgerQueryService ledgerQueryService;
    private final FinancialTransactionRepository transactionRepository;
    private final FloatPredictionRepository predictionRepository;
    private final AuditService auditService;
    private final AppProperties properties;

    @Transactional(readOnly = true)
    public Agent require(UUID agentId) {
        return agentRepository.findWithDetailsById(agentId).orElseThrow(() -> BusinessException.notFound("Agent", agentId));
    }

    @Transactional(readOnly = true)
    public AgentProfile profile(UUID agentId) {
        Agent a = require(agentId);
        AgentAccount acc = accountRepository.findByAgent_Id(agentId).orElseThrow();
        return new AgentProfile(a.getId(), a.getAgentCode(), a.getBusinessName(), a.getUser().getId(), a.getUser().getFullName(),
                a.getUser().getUsername(), a.getUser().getEmail(), a.getUser().getPhoneNumber(), a.getArea().getId(),
                a.getArea().getCode(), a.getArea().getName(), a.getTier(), a.getStatus(), a.getRiskLevel(), a.getCreditScore(),
                Money.of(a.getCreditLimit()), a.getLatitude(), a.getLongitude(), a.getAddress(), Money.of(a.getMinEfloatThreshold()),
                Money.of(a.getMinCashThreshold()), a.getOnboardedAt(), acc.getAccountNumber(), Money.of(acc.getDailyTransactionLimit()),
                acc.getStatus());
    }

    @Transactional(readOnly = true)
    public AgentBalanceView balances(UUID agentId) {
        Agent a = require(agentId);
        AgentAccount acc = accountRepository.findByAgent_Id(agentId).orElseThrow();
        BalanceSnapshot b = accounts.agentBalances(agentId);
        BigDecimal usedToday = Money.of(transactionRepository.sumAgentAmountSince(agentId,
                EnumSet.of(TransactionType.CASH_DEPOSIT, TransactionType.CASH_WITHDRAWAL, TransactionType.AGENT_TRANSFER_TO_CUSTOMER),
                AppClock.today().atStartOfDay(AppClock.zone()).toInstant()));
        return new AgentBalanceView(agentId, a.getAgentCode(), a.getBusinessName(), properties.getCurrency(), b,
                Money.of(a.getMinEfloatThreshold()), Money.of(a.getMinCashThreshold()),
                b.efloat().compareTo(a.getMinEfloatThreshold()) < 0, b.cash().compareTo(a.getMinCashThreshold()) < 0,
                Money.of(a.getCreditLimit()), Money.max(Money.ZERO, Money.of(a.getCreditLimit()).subtract(b.advancePrincipalOutstanding())),
                Money.of(acc.getDailyTransactionLimit()), usedToday, AppClock.now());
    }

    @Transactional(readOnly = true)
    public PageResponse<StatementLine> statement(UUID agentId, BalanceType type, Pageable pageable) {
        if (!LedgerAccountService.AGENT_BALANCE_TYPES.contains(type)) {
            throw BusinessException.badRequest("INVALID_BALANCE_TYPE", "Statement type must be one of " + LedgerAccountService.AGENT_BALANCE_TYPES);
        }
        return ledgerQueryService.statement(accounts.agent(agentId, type).getId(), pageable);
    }

    @Transactional(readOnly = true)
    public List<AgentSummary> summaries(UUID areaId) {
        List<Agent> agents = areaId == null ? agentRepository.findAllByOrderByAgentCode() : agentRepository.findByArea_IdOrderByAgentCode(areaId);
        return agents.stream().map(a -> {
            BalanceSnapshot b = accounts.agentBalances(a.getId());
            return new AgentSummary(a.getId(), a.getAgentCode(), a.getBusinessName(), a.getUser().getFullName(), a.getArea().getCode(),
                    a.getStatus(), a.getRiskLevel(), a.getTier(), a.getCreditScore(), b.efloat(), b.cash(), b.commissionPayable(),
                    Money.of(b.totalAdvanceOutstanding()), b.efloat().compareTo(a.getMinEfloatThreshold()) < 0,
                    b.cash().compareTo(a.getMinCashThreshold()) < 0);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<AgentLocation> locations(UUID areaId) {
        List<Agent> agents = areaId == null ? agentRepository.findAllByOrderByAgentCode() : agentRepository.findByArea_IdOrderByAgentCode(areaId);
        return agents.stream().map(a -> {
            BalanceSnapshot b = accounts.agentBalances(a.getId());
            var risk = predictionRepository.findTopByAgent_IdOrderByCreatedAtDesc(a.getId()).map(FloatPrediction::getRiskType).orElse(null);
            return new AgentLocation(a.getId(), a.getAgentCode(), a.getBusinessName(), a.getAddress(), a.getLatitude(), a.getLongitude(),
                    a.getStatus(), a.getRiskLevel(), b.efloat(), b.cash(), b.efloat().compareTo(a.getMinEfloatThreshold()) < 0,
                    b.cash().compareTo(a.getMinCashThreshold()) < 0, risk);
        }).toList();
    }

    @Transactional
    public AgentProfile update(UUID agentId, UpdateAgentRequest r) {
        Agent a = require(agentId);
        AgentAccount acc = accountRepository.findByAgent_Id(agentId).orElseThrow();
        Map<String, Object> changes = new LinkedHashMap<>();
        if (r.businessName() != null) {
            a.setBusinessName(r.businessName());
            changes.put("businessName", r.businessName());
        }
        if (r.address() != null) {
            a.setAddress(r.address());
            changes.put("address", r.address());
        }
        if (r.latitude() != null && r.longitude() != null) {
            a.setLatitude(r.latitude());
            a.setLongitude(r.longitude());
            changes.put("location", r.latitude() + "," + r.longitude());
        }
        if (r.minEfloatThreshold() != null) {
            a.setMinEfloatThreshold(r.minEfloatThreshold());
            changes.put("minEfloatThreshold", r.minEfloatThreshold());
        }
        if (r.minCashThreshold() != null) {
            a.setMinCashThreshold(r.minCashThreshold());
            changes.put("minCashThreshold", r.minCashThreshold());
        }
        if (r.dailyTransactionLimit() != null) {
            acc.setDailyTransactionLimit(r.dailyTransactionLimit());
            changes.put("dailyTransactionLimit", r.dailyTransactionLimit());
        }
        auditService.record("AGENT_UPDATED", "AGENT", agentId, changes);
        return profile(agentId);
    }
}
