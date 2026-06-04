package com.gursangat.flightbooking.service;

import com.gursangat.flightbooking.exception.FlightNotFoundException;
import com.gursangat.flightbooking.exception.SeatsUnavailableException;
import com.gursangat.flightbooking.model.Booking;
import com.gursangat.flightbooking.model.Flight;
import com.gursangat.flightbooking.repository.BookingRepository;
import com.gursangat.flightbooking.repository.FlightRepository;
import com.gursangat.flightbooking.repository.IdempotencyStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class BookingService {

    private final FlightRepository flightRepository;
    private final BookingRepository bookingRepository;
    private final IdempotencyStore idempotencyStore;

    public BookingService(FlightRepository flightRepository,
                          BookingRepository bookingRepository,
                          IdempotencyStore idempotencyStore) {
        this.flightRepository = flightRepository;
        this.bookingRepository = bookingRepository;
        this.idempotencyStore = idempotencyStore;
    }

    /**
     * Books the requested number of seats on a flight.
     *
     * <p>When an {@code idempotencyKey} is supplied, the booking is created at
     * most once for that key: a retried or concurrently duplicated request with
     * the same key returns the original booking instead of consuming more seats.
     *
     * @param idempotencyKey optional client-supplied key; may be null/blank to opt out
     * @throws FlightNotFoundException   if the flight number is unknown
     * @throws SeatsUnavailableException if there are not enough free seats
     */
    public BookingResult book(String flightNumber, String passengerName, int seats, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return new BookingResult(createBooking(flightNumber, passengerName, seats), true);
        }

        // Fingerprint of the request: lets the store tell a genuine retry (same
        // key, same body -> replay) apart from a key reused for a different
        // request (same key, different body -> reject).
        String requestFingerprint = flightNumber + "|" + passengerName + "|" + seats;

        // created[0] is flipped only by the thread whose supplier actually runs;
        // concurrent callers with the same key block, then read the stored booking
        // with created == false. This keeps the duplicate-suppression race-free.
        boolean[] created = {false};
        Booking booking = idempotencyStore.computeIfAbsent(idempotencyKey.trim(), requestFingerprint, () -> {
            created[0] = true;
            return createBooking(flightNumber, passengerName, seats);
        });
        return new BookingResult(booking, created[0]);
    }

    private Booking createBooking(String flightNumber, String passengerName, int seats) {
        Flight flight = flightRepository.findByFlightNumber(flightNumber)
                .orElseThrow(() -> new FlightNotFoundException(flightNumber));

        // Atomic check-and-reserve inside the model; prevents the overbooking
        // race a check-then-set version would be exposed to when two passengers
        // book the last seats at the same time.
        if (!flight.reserve(seats)) {
            throw new SeatsUnavailableException(flightNumber, seats, flight.getAvailableSeats());
        }

        Booking booking = new Booking(
                UUID.randomUUID().toString(),
                flightNumber,
                passengerName,
                seats,
                Instant.now());

        return bookingRepository.save(booking);
    }
}
