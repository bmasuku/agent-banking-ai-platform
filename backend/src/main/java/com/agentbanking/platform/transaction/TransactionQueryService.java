package com.agentbanking.platform.transaction;

import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.fraud.FraudCheckRepository;
import com.agentbanking.platform.ledger.LedgerAccountService;
import com.agentbanking.platform.ledger.LedgerQueryService;
import com.agentbanking.platform.transaction.TransactionDtos.CustomerRef;
import com.agentbanking.platform.transaction.TransactionDtos.FraudSummary;
import com.agentbanking.platform.transaction.TransactionDtos.TransactionResponse;
import com.agentbanking.platform.transaction.TransactionDtos.TransactionSummary;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionQueryService {

    private final FinancialTransactionRepository repository;
    private final FraudCheckRepository fraudCheckRepository;
    private final LedgerQueryService ledgerQueryService;
    private final LedgerAccountService accounts;

    public record Filter(UUID agentId, UUID areaId, UUID customerId, TransactionType type, TransactionStatus status,
                         Instant from, Instant to, String reference) {
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionSummary> search(Filter f, Pageable pageable) {
        Specification<FinancialTransaction> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.agentId() != null) {
                p.add(cb.or(cb.equal(root.get("agent").get("id"), f.agentId()),
                        cb.equal(root.get("counterpartyAgent").get("id"), f.agentId())));
            }
            if (f.areaId() != null) {
                p.add(cb.equal(root.get("areaId"), f.areaId()));
            }
            if (f.customerId() != null) {
                p.add(cb.equal(root.get("customer").get("id"), f.customerId()));
            }
            if (f.type() != null) {
                p.add(cb.equal(root.get("type"), f.type()));
            }
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from()));
            }
            if (f.to() != null) {
                p.add(cb.lessThan(root.get("createdAt"), f.to()));
            }
            if (f.reference() != null && !f.reference().isBlank()) {
                p.add(cb.equal(root.get("reference"), f.reference().trim().toUpperCase()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(repository.findAll(spec, pageable), TransactionSummary::from);
    }

    @Transactional(readOnly = true)
    public FinancialTransaction require(UUID id) {
        return repository.findDetailedById(id).orElseThrow(() -> BusinessException.notFound("Transaction", id));
    }

    @Transactional(readOnly = true)
    public TransactionResponse detail(UUID id) {
        return toResponse(require(id), false);
    }

    /** Full view of a transaction including its journal lines, fraud evaluation and the agent's current balances. */
    @Transactional(readOnly = true)
    public TransactionResponse toResponse(FinancialTransaction t, boolean replayed) {
        var customer = t.getCustomer() == null ? null : new CustomerRef(t.getCustomer().getId(), t.getCustomer().getCustomerNumber(),
                t.getCustomer().getFullName(), t.getCustomer().getPhoneNumber());
        var fraud = fraudCheckRepository.findByTransaction_Id(t.getId()).map(FraudSummary::from).orElse(null);
        var balances = t.getAgent() == null ? null : accounts.agentBalances(t.getAgent().getId());
        return new TransactionResponse(t.getId(), t.getReference(), t.getType(), t.getStatus(),
                t.getAgent() == null ? null : t.getAgent().getId(), t.getAgent() == null ? null : t.getAgent().getAgentCode(),
                t.getCounterpartyAgent() == null ? null : t.getCounterpartyAgent().getId(),
                t.getCounterpartyAgent() == null ? null : t.getCounterpartyAgent().getAgentCode(),
                customer, Money.of(t.getAmount()), Money.of(t.getFeeAmount()), Money.of(t.getCommissionAmount()), t.getCurrency(),
                t.getDescription(), t.getFailureReason(), t.getCreatedAt(), balances, fraud,
                ledgerQueryService.entriesFor(t.getId()), replayed);
    }
}
