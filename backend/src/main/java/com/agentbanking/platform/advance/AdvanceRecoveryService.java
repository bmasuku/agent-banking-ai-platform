package com.agentbanking.platform.advance;

import com.agentbanking.platform.agent.Agent;
import com.agentbanking.platform.commission.CommissionDisbursement;
import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.transaction.FinancialTransaction;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Applies already-posted recovery amounts to individual advance records. Must run in the same
 * transaction as the ledger posting that moved ADVANCE_INTEREST / ADVANCE_PRINCIPAL.
 */
@Service
@RequiredArgsConstructor
public class AdvanceRecoveryService {

    private final FloatAdvanceRepository advanceRepository;
    private final AdvanceRepaymentRepository repaymentRepository;

    /** Distributes recovered interest and principal across the agent's outstanding advances, earliest due first. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<AdvanceRepayment> applyAcrossAdvances(Agent agent, BigDecimal interest, BigDecimal principal,
                                                      FinancialTransaction transaction, RepaymentSource source,
                                                      CommissionDisbursement disbursement, UUID recordedBy) {
        BigDecimal remainingInterest = Money.of(interest);
        BigDecimal remainingPrincipal = Money.of(principal);
        List<AdvanceRepayment> repayments = new ArrayList<>();
        for (FloatAdvance advance : advanceRepository.findByAgentAndStatuses(agent.getId(),
                EnumSet.of(AdvanceStatus.ACTIVE, AdvanceStatus.OVERDUE))) {
            if (remainingInterest.signum() == 0 && remainingPrincipal.signum() == 0) {
                break;
            }
            BigDecimal i = Money.min(remainingInterest, Money.of(advance.getInterestOutstanding()));
            BigDecimal p = Money.min(remainingPrincipal, Money.of(advance.getPrincipalOutstanding()));
            if (i.signum() == 0 && p.signum() == 0) {
                continue;
            }
            repayments.add(applyToAdvance(advance, i, p, transaction, source, disbursement, recordedBy));
            remainingInterest = remainingInterest.subtract(i);
            remainingPrincipal = remainingPrincipal.subtract(p);
        }
        if (remainingInterest.signum() != 0 || remainingPrincipal.signum() != 0) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "ADVANCE_LEDGER_MISMATCH", "Advance records do not match the agent's advance ledger balances");
        }
        return repayments;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AdvanceRepayment applyToAdvance(FloatAdvance advance, BigDecimal interest, BigDecimal principal,
                                           FinancialTransaction transaction, RepaymentSource source,
                                           CommissionDisbursement disbursement, UUID recordedBy) {
        advance.setInterestOutstanding(Money.of(advance.getInterestOutstanding().subtract(interest)));
        advance.setPrincipalOutstanding(Money.of(advance.getPrincipalOutstanding().subtract(principal)));
        advance.setInterestRepaid(Money.of(advance.getInterestRepaid().add(interest)));
        advance.setPrincipalRepaid(Money.of(advance.getPrincipalRepaid().add(principal)));
        if (advance.getPrincipalOutstanding().signum() == 0 && advance.getInterestOutstanding().signum() == 0) {
            advance.setStatus(AdvanceStatus.REPAID);
            advance.setClosedAt(AppClock.now());
        }

        AdvanceRepayment repayment = new AdvanceRepayment();
        repayment.setAdvance(advance);
        repayment.setAgent(advance.getAgent());
        repayment.setSource(source);
        repayment.setInterestAmount(Money.of(interest));
        repayment.setPrincipalAmount(Money.of(principal));
        repayment.setTotalAmount(Money.of(interest.add(principal)));
        repayment.setTransaction(transaction);
        repayment.setDisbursement(disbursement);
        repayment.setRecordedBy(recordedBy);
        return repaymentRepository.save(repayment);
    }
}
