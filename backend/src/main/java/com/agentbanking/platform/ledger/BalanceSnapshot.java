package com.agentbanking.platform.ledger;

import java.math.BigDecimal;

public record BalanceSnapshot(
        BigDecimal efloat,
        BigDecimal cash,
        BigDecimal commissionPayable,
        BigDecimal advancePrincipalOutstanding,
        BigDecimal advanceInterestOutstanding) {

    public BigDecimal totalAdvanceOutstanding() {
        return advancePrincipalOutstanding.add(advanceInterestOutstanding);
    }
}
