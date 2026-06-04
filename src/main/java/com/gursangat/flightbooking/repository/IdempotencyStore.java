package com.gursangat.flightbooking.repository;

import com.gursangat.flightbooking.exception.IdempotencyConflictException;
import com.gursangat.flightbooking.model.Booking;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * In-memory store mapping an idempotency key to the booking it produced (together
 * with a fingerprint of the request that created it).
 *
 * <p>{@link #computeIfAbsent} runs the supplied booking logic at most once per
 * key: concurrent requests carrying the same key block until the first one
 * finishes and then receive that same booking, so a retried (or accidentally
 * double-submitted) request never creates a second booking.
 *
 * <p>If the same key is replayed with a <em>different</em> request body, the call
 * is rejected with {@link IdempotencyConflictException} rather than returning the
 * original (now mismatched) booking.
 */
@Repository
public class IdempotencyStore {

    private record Entry(String requestFingerprint, Booking booking) {
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public Booking computeIfAbsent(String key, String requestFingerprint, Supplier<Booking> bookingSupplier) {
        // compute() runs the remapping function atomically for the key, so the
        // create / replay / conflict decision and the booking happen as one unit.
        Entry entry = store.compute(key, (k, existing) -> {
            if (existing == null) {
                return new Entry(requestFingerprint, bookingSupplier.get());
            }
            if (!existing.requestFingerprint().equals(requestFingerprint)) {
                throw new IdempotencyConflictException(key);
            }
            return existing; // genuine retry of the same request -> replay
        });
        return entry.booking();
    }
}
