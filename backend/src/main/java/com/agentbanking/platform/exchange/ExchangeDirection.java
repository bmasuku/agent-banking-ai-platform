package com.agentbanking.platform.exchange;

/** Direction from the requester's point of view. */
public enum ExchangeDirection {
    /** Requester sends e-float to the counterparty and receives the same amount in cash. */
    EFLOAT_FOR_CASH,
    /** Requester sends cash to the counterparty and receives the same amount in e-float. */
    CASH_FOR_EFLOAT
}
