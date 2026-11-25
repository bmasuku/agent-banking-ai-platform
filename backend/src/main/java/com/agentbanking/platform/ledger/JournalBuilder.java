package com.agentbanking.platform.ledger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Fluent builder for journal legs. Zero amounts are skipped so optional fees/commissions need no branching. */
public final class JournalBuilder {

    private final List<PostingLeg> legs = new ArrayList<>();

    public static JournalBuilder journal() {
        return new JournalBuilder();
    }

    public JournalBuilder debit(LedgerAccount account, BigDecimal amount, String memo) {
        if (amount != null && amount.signum() > 0) {
            legs.add(new PostingLeg(account.getId(), EntrySide.DEBIT, amount, memo));
        }
        return this;
    }

    public JournalBuilder credit(LedgerAccount account, BigDecimal amount, String memo) {
        if (amount != null && amount.signum() > 0) {
            legs.add(new PostingLeg(account.getId(), EntrySide.CREDIT, amount, memo));
        }
        return this;
    }

    /** Debit one account and credit another with the same amount. */
    public JournalBuilder pair(LedgerAccount debitAccount, LedgerAccount creditAccount, BigDecimal amount, String memo) {
        return debit(debitAccount, amount, memo).credit(creditAccount, amount, memo);
    }

    public List<PostingLeg> legs() {
        return List.copyOf(legs);
    }
}
