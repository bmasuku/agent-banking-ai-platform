package com.agentbanking.platform.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 201 for newly created financial operations; 200 with {@code Idempotency-Replayed: true} for replays. */
public final class IdempotentResponses {

    public static final String REPLAYED_HEADER = "Idempotency-Replayed";

    private IdempotentResponses() {
    }

    public static <T> ResponseEntity<T> of(T body, boolean replayed) {
        return of(body, replayed, HttpStatus.CREATED);
    }

    public static <T> ResponseEntity<T> of(T body, boolean replayed, HttpStatus createdStatus) {
        if (replayed) {
            return ResponseEntity.ok().header(REPLAYED_HEADER, "true").body(body);
        }
        return ResponseEntity.status(createdStatus).body(body);
    }
}
