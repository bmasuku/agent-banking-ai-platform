package com.agentbanking.platform.ledger;

import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.common.PageResponse;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only views over the immutable journal. */
@Service
@RequiredArgsConstructor
public class LedgerQueryService {

    private final LedgerEntryRepository entryRepository;
    private final LedgerAccountRepository accountRepository;

    @Transactional(readOnly = true)
    public List<LedgerEntryView> entriesFor(UUID transactionId) {
        return entryRepository.findByTransactionWithAccounts(transactionId).stream().map(LedgerEntryView::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<StatementLine> statement(UUID accountId, Pageable pageable) {
        LedgerAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> BusinessException.notFound("Ledger account", accountId));
        return PageResponse.of(entryRepository.findStatement(accountId, pageable), e -> StatementLine.from(e, account));
    }

    public record LedgerEntryView(int lineNumber, String accountCode, String accountName, BalanceType balanceType,
                                  EntrySide side, BigDecimal amount, BigDecimal balanceAfter, String memo) {
        public static LedgerEntryView from(LedgerEntry e) {
            return new LedgerEntryView(e.getLineNumber(), e.getAccount().getCode(), e.getAccount().getName(),
                    e.getAccount().getBalanceType(), e.getSide(), Money.of(e.getAmount()), Money.of(e.getBalanceAfter()), e.getMemo());
        }
    }

    /**
     * @param signedAmount movement in the account's natural direction (positive increases the balance)
     */
    public record StatementLine(UUID entryId, UUID transactionId, String reference, TransactionType transactionType,
                                EntrySide side, BigDecimal amount, BigDecimal signedAmount, BigDecimal balanceAfter,
                                String memo, Instant createdAt) {
        static StatementLine from(LedgerEntry e, LedgerAccount account) {
            BigDecimal signed = account.signedDelta(e.getSide(), e.getAmount());
            return new StatementLine(e.getId(), e.getTransaction().getId(), e.getTransaction().getReference(),
                    e.getTransaction().getType(), e.getSide(), Money.of(e.getAmount()), Money.of(signed),
                    Money.of(e.getBalanceAfter()), e.getMemo(), e.getCreatedAt());
        }
    }
}
