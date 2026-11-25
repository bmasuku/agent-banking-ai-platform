package com.agentbanking.platform.ledger;

import com.agentbanking.platform.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class JournalValidator {

    private JournalValidator() {
    }

    public static void validate(List<PostingLeg> legs) {
        if (legs == null || legs.size() < 2) {
            throw imbalance("A journal requires at least two legs", Map.of());
        }
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (PostingLeg leg : legs) {
            if (leg.accountId() == null || leg.side() == null || leg.amount() == null) {
                throw imbalance("Journal leg is incomplete", Map.of());
            }
            if (leg.amount().signum() <= 0) {
                throw imbalance("Journal leg amounts must be positive", Map.of("amount", leg.amount()));
            }
            if (leg.amount().stripTrailingZeros().scale() > 4) {
                throw imbalance("Journal leg amounts support at most 4 decimal places", Map.of("amount", leg.amount()));
            }
            if (leg.side() == EntrySide.DEBIT) {
                debits = debits.add(leg.amount());
            } else {
                credits = credits.add(leg.amount());
            }
        }
        if (debits.signum() == 0 || credits.signum() == 0 || debits.compareTo(credits) != 0) {
            throw imbalance("Journal is not balanced", Map.of("debits", debits, "credits", credits));
        }
    }

    private static BusinessException imbalance(String message, Map<String, Object> details) {
        return new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "LEDGER_IMBALANCE", message, details);
    }
}
