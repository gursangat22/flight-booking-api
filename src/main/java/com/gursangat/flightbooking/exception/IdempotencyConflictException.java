package com.gursangat.flightbooking.exception;

/**
 * Thrown when an {@code Idempotency-Key} that was already used is replayed with a
 * <em>different</em> request body. Returning the original booking in that case
 * would silently give the caller the wrong result, so the request is rejected.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String key) {
        super("Idempotency-Key '" + key + "' was already used for a different request");
    }
}
