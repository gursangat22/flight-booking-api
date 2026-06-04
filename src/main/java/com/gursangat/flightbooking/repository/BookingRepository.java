package com.gursangat.flightbooking.repository;

import com.gursangat.flightbooking.model.Booking;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of confirmed bookings, keyed by booking id.
 */
@Repository
public class BookingRepository {

    private final Map<String, Booking> bookings = new ConcurrentHashMap<>();

    public Booking save(Booking booking) {
        bookings.put(booking.getId(), booking);
        return booking;
    }
}
