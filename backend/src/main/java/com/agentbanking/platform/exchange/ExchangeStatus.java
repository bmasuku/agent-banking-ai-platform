package com.agentbanking.platform.exchange;

public enum ExchangeStatus {
    /** Anonymous open listing — amount sought, identity hidden. */
    OPEN,
    /** Legacy directed proposal to a named counterparty. */
    PENDING,
    /** Requester accepted an interest; identities revealed to matched parties only. */
    MATCHED,
    /** Both parties entered each other's meetup verification tokens. */
    VERIFIED,
    COMPLETED,
    REJECTED,
    CANCELLED,
    EXPIRED
}
