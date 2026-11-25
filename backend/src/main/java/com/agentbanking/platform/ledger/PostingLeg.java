package com.agentbanking.platform.ledger;

import java.math.BigDecimal;
import java.util.UUID;

public record PostingLeg(UUID accountId, EntrySide side, BigDecimal amount, String memo) {
}
