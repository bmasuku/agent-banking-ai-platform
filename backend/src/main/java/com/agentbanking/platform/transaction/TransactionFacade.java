package com.agentbanking.platform.transaction;

import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.customer.CustomerService;
import com.agentbanking.platform.fraud.FraudAssessment;
import com.agentbanking.platform.fraud.FraudDetectionService;
import com.agentbanking.platform.fraud.FraudFeatureService;
import com.agentbanking.platform.fraud.FraudFeatures;
import com.agentbanking.platform.ledger.BalanceSnapshot;
import com.agentbanking.platform.ledger.LedgerAccountService;
import com.agentbanking.platform.transaction.TariffCalculator.TariffQuote;
import com.agentbanking.platform.transaction.TransactionDtos.CustomerTransactionRequest;
import com.agentbanking.platform.transaction.TransactionDtos.QuoteResponse;
import com.agentbanking.platform.transaction.TransactionDtos.TransactionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Entry point for customer transactions. Runs fraud scoring (possibly a remote AI call) outside the
 * database transaction, then hands over to the transactional {@link TransactionService}.
 */
@Service
@RequiredArgsConstructor
public class TransactionFacade {

    private final AgentTransactionGuard guard;
    private final FraudFeatureService fraudFeatureService;
    private final FraudDetectionService fraudDetectionService;
    private final TransactionService transactionService;
    private final TransactionQueryService queryService;
    private final TariffService tariffService;
    private final LedgerAccountService accounts;

    public TransactionResult execute(UUID agentId, UUID initiatorId, TransactionType type, CustomerTransactionRequest request,
                                     String idempotencyKey) {
        AgentTransactionGuard.validateKey(idempotencyKey);
        BigDecimal amount = Money.requireValidAmount(request.amount(), "amount");
        String phone = CustomerService.normalizePhone(request.customerPhone());
        String hash = AgentTransactionGuard.requestHash(type, agentId, amount, phone);

        Optional<FinancialTransaction> replay = guard.findReplay(idempotencyKey, hash, initiatorId);
        if (replay.isPresent()) {
            return new TransactionResult(queryService.toResponse(queryService.require(replay.get().getId()), true), false);
        }

        FraudFeatures features = fraudFeatureService.build(agentId, type, amount, phone, request.latitude(), request.longitude());
        FraudAssessment assessment = fraudDetectionService.assess(features);
        return transactionService.execute(new TransactionService.Command(agentId, initiatorId, type, amount, phone,
                request.customerName(), request.customerNationalId(), request.narration(), request.latitude(), request.longitude(),
                idempotencyKey, hash, features, assessment));
    }

    public QuoteResponse quote(UUID agentId, TransactionType type, BigDecimal requestedAmount) {
        if (!type.isCustomerTransaction()) {
            throw BusinessException.badRequest("INVALID_TYPE",
                    "Quotes are available for CASH_DEPOSIT, CASH_WITHDRAWAL and AGENT_TRANSFER_TO_CUSTOMER");
        }
        BigDecimal amount = Money.requireValidAmount(requestedAmount, "amount");
        TariffQuote q = tariffService.quote(type, amount);
        BalanceSnapshot b = accounts.agentBalances(agentId);
        BigDecimal efloatChange;
        BigDecimal cashChange;
        BigDecimal customerPays;
        BigDecimal customerReceives;
        String explanation;
        switch (type) {
            case CASH_DEPOSIT -> {
                efloatChange = amount.negate();
                cashChange = amount;
                customerPays = amount;
                customerReceives = amount.subtract(q.fee());
                explanation = "Customer hands over " + amount + " cash; " + customerReceives + " is credited to the customer's wallet "
                        + "from your e-float. You earn " + q.commission() + " commission.";
            }
            case CASH_WITHDRAWAL -> {
                efloatChange = amount;
                cashChange = amount.negate();
                customerPays = amount.add(q.fee());
                customerReceives = amount;
                explanation = "Customer's wallet is debited " + customerPays + " (including fee " + q.fee() + "); you receive "
                        + amount + " e-float and pay out " + amount + " cash. You earn " + q.commission() + " commission.";
            }
            default -> {
                efloatChange = amount.negate();
                cashChange = Money.ZERO;
                customerPays = Money.ZERO;
                customerReceives = amount.subtract(q.fee());
                explanation = amount + " is sent from your e-float to the customer's wallet. You earn " + q.commission() + " commission.";
            }
        }
        BigDecimal projectedEfloat = Money.of(b.efloat().add(efloatChange));
        BigDecimal projectedCash = Money.of(b.cash().add(cashChange));
        boolean sufficient = projectedEfloat.signum() >= 0 && projectedCash.signum() >= 0;
        return new QuoteResponse(type, amount, q.fee(), q.commission(), Money.of(customerPays), Money.of(customerReceives),
                Money.of(efloatChange), Money.of(cashChange), b.efloat(), b.cash(), projectedEfloat, projectedCash, sufficient,
                sufficient ? explanation : explanation + " Insufficient " + (projectedEfloat.signum() < 0 ? "e-float" : "cash") + " to complete it.");
    }
}
