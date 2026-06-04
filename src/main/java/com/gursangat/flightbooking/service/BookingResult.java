package com.gursangat.flightbooking.service;

import com.gursangat.flightbooking.model.Booking;

/**
 * Outcome of a booking attempt.
 *
 * @param booking the confirmed booking
 * @param created true if this call created the booking, false if it replayed an
 *                existing one matched by its idempotency key
 */
public record BookingResult(Booking booking, boolean created) {
}
