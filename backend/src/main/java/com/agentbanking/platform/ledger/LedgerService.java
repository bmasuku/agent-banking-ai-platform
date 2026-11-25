package com.agentbanking.platform.ledger;

import com.agentbanking.platform.common.AppClock;
import com.agentbanking.platform.common.Money;
import com.agentbanking.platform.common.exception.BusinessException;
import com.agentbanking.platform.transaction.FinancialTransaction;
import com.agentbanking.platform.transaction.FinancialTransactionRepository;
import com.agentbanking.platform.transaction.TransactionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The only component allowed to change ledger balances.
 *
 * <ul>
 *     <li>Validates that the journal is balanced (sum of debits = sum of credits).</li>
 *     <li>Locks every affected account with SELECT ... FOR UPDATE in ascending id order (deadlock-free).</li>
 *     <li>Rejects journals that would leave a non-overdraftable account negative.</li>
 *     <li>Writes immutable ledger entries with the running balance after each line.</li>
 * </ul>
 * Must run inside the caller's transaction so business records and ledger postings commit atomically.
 */
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final EntityManager entityManager;
    private final FinancialTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public PostingResult post(FinancialTransaction header, List<PostingLeg> legs) {
        JournalValidator.validate(legs);
        entityManager.flush();

        Map<UUID, LedgerAccount> accounts = lockAccounts(legs);
        Map<UUID, BigDecimal> running = new HashMap<>();
        accounts.forEach((id, account) -> running.put(id, account.getBalance()));

        if (header.getCreatedAt() == null) {
            header.setCreatedAt(AppClock.now());
        }
        header.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(header);

        List<LedgerEntry> entries = new ArrayList<>(legs.size());
        int line = 1;
        for (PostingLeg leg : legs) {
            LedgerAccount account = accounts.get(leg.accountId());
            BigDecimal next = running.get(account.getId()).add(account.signedDelta(leg.side(), leg.amount()));
            running.put(account.getId(), next);
            entries.add(new LedgerEntry(header, account, leg.side(), leg.amount(), next, line++, leg.memo(), header.getCreatedAt()));
        }

        for (LedgerAccount account : accounts.values()) {
            BigDecimal finalBalance = running.get(account.getId());
            if (!account.isAllowNegative() && finalBalance.signum() < 0) {
                BalanceType type = account.getBalanceType();
                throw BusinessException.unprocessable(type.errorCode(),
                        "Insufficient " + type.label().toLowerCase() + " balance on account " + account.getCode(),
                        Map.of("account", account.getCode(),
                                "balanceType", type.name(),
                                "available", Money.of(account.getBalance()),
                                "shortfall", Money.of(finalBalance.negate())));
            }
        }
        accounts.values().forEach(account -> account.setBalance(running.get(account.getId())));
        entryRepository.saveAll(entries);
        return new PostingResult(header, entries, accounts);
    }

    private Map<UUID, LedgerAccount> lockAccounts(List<PostingLeg> legs) {
        Map<UUID, LedgerAccount> accounts = new LinkedHashMap<>();
        legs.stream().map(PostingLeg::accountId).distinct().sorted().forEach(id -> {
            LedgerAccount account = entityManager.find(LedgerAccount.class, id);
            if (account == null) {
                throw new BusinessException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                        "LEDGER_ACCOUNT_MISSING", "Ledger account not found: " + id);
            }
            // refresh re-reads the row under a pessimistic lock, discarding any stale in-memory state
            entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE);
            accounts.put(id, account);
        });
        return accounts;
    }

    public record PostingResult(FinancialTransaction transaction, List<LedgerEntry> entries, Map<UUID, LedgerAccount> accounts) {

        public BigDecimal balanceOf(LedgerAccount account) {
            LedgerAccount locked = accounts.get(account.getId());
            return locked != null ? locked.getBalance() : account.getBalance();
        }
    }
}
