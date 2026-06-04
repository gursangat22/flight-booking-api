package com.gursangat.flightbooking.repository;

import com.gursangat.flightbooking.model.Booking;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * In-memory store mapping an idempotency key to the booking it produced.
 *
 * <p>{@link #computeIfAbsent} runs the supplied booking logic at most once per
 * key: concurrent requests carrying the same key block until the first one
 * finishes and then receive that same booking, so a retried (or accidentally
 * double-submitted) request never creates a second booking.
 */
@Repository
public class IdempotencyStore {

    private final Map<String, Booking> store = new ConcurrentHashMap<>();

    public Booking computeIfAbsent(String key, Supplier<Booking> bookingSupplier) {
        return store.computeIfAbsent(key, k -> bookingSupplier.get());
    }
}
