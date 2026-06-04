package com.gursangat.flightbooking.service;

import com.gursangat.flightbooking.exception.FlightNotFoundException;
import com.gursangat.flightbooking.exception.IdempotencyConflictException;
import com.gursangat.flightbooking.exception.SeatsUnavailableException;
import com.gursangat.flightbooking.model.Booking;
import com.gursangat.flightbooking.model.Flight;
import com.gursangat.flightbooking.repository.BookingRepository;
import com.gursangat.flightbooking.repository.FlightRepository;
import com.gursangat.flightbooking.repository.IdempotencyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingServiceTest {

    private FlightRepository flightRepository;
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        flightRepository = new FlightRepository();
        flightRepository.save(new Flight("AI101", 3));
        flightRepository.save(new Flight("AI202", 50));
        flightRepository.save(new Flight("AI303", 1));
        bookingService = new BookingService(flightRepository, new BookingRepository(), new IdempotencyStore());
    }

    @Test
    void booksSeatsOnAKnownFlight() {
        Booking booking = bookingService.book("AI202", "Alice", 2, null).booking();

        assertNotNull(booking.getId());
        assertEquals("AI202", booking.getFlightNumber());
        assertEquals(2, booking.getSeats());
    }

    @Test
    void rejectsUnknownFlight() {
        assertThrows(FlightNotFoundException.class,
                () -> bookingService.book("ZZ999", "Bob", 1, null));
    }

    @Test
    void rejectsOverbooking() {
        // AI303 has capacity 1
        bookingService.book("AI303", "Carol", 1, null);

        assertThrows(SeatsUnavailableException.class,
                () -> bookingService.book("AI303", "Dave", 1, null));
    }

    @Test
    void fillsFlightExactlyToCapacity() {
        // AI101 has capacity 3
        bookingService.book("AI101", "P1", 3, null);

        assertThrows(SeatsUnavailableException.class,
                () -> bookingService.book("AI101", "P2", 1, null));
    }

    @Test
    void sameIdempotencyKeyReturnsSameBookingAndConsumesSeatsOnce() {
        BookingResult first = bookingService.book("AI202", "Alice", 2, "key-123");
        BookingResult second = bookingService.book("AI202", "Alice", 2, "key-123");

        assertTrue(first.created());
        assertFalse(second.created()); // replayed, not created again
        assertEquals(first.booking().getId(), second.booking().getId());

        // Only the first booking consumed seats: 50 - 2 = 48 remain, not 46.
        assertEquals(48, flightRepository.findByFlightNumber("AI202").orElseThrow().getAvailableSeats());
    }

    @Test
    void sameKeyWithDifferentBodyIsRejected() {
        bookingService.book("AI202", "Alice", 1, "dup-key");

        // Same key, different passenger -> must not silently replay Alice's booking.
        assertThrows(IdempotencyConflictException.class,
                () -> bookingService.book("AI202", "Bob", 1, "dup-key"));

        // The mismatched second request consumed no seats.
        assertEquals(49, flightRepository.findByFlightNumber("AI202").orElseThrow().getAvailableSeats());
    }

    @Test
    void differentIdempotencyKeysCreateDistinctBookings() {
        BookingResult a = bookingService.book("AI202", "Alice", 1, "key-a");
        BookingResult b = bookingService.book("AI202", "Bob", 1, "key-b");

        assertNotEquals(a.booking().getId(), b.booking().getId());
        assertEquals(48, flightRepository.findByFlightNumber("AI202").orElseThrow().getAvailableSeats());
    }

    @Test
    void concurrentRequestsWithSameKeyCreateExactlyOneBooking() throws InterruptedException {
        int attempts = 50;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger createdCount = new AtomicInteger();
        Set<String> bookingIds = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < attempts; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    BookingResult result = bookingService.book("AI202", "Alice", 1, "same-key");
                    bookingIds.add(result.booking().getId());
                    if (result.created()) {
                        createdCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(1, createdCount.get());   // booking created exactly once
        assertEquals(1, bookingIds.size());    // everyone saw the same booking
        // Only one seat consumed despite 50 concurrent calls.
        assertEquals(49, flightRepository.findByFlightNumber("AI202").orElseThrow().getAvailableSeats());
    }

    @Test
    void doesNotOverbookUnderConcurrentRequests() throws InterruptedException {
        int capacity = 50; // AI202
        int attempts = 200;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();

        for (int i = 0; i < attempts; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    bookingService.book("AI202", "Passenger", 1, null);
                    successes.incrementAndGet();
                } catch (SeatsUnavailableException ignored) {
                    // expected once the flight is full
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        start.countDown(); // release all threads at once to maximise contention
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        // Exactly capacity seats booked, never more — no overbooking.
        assertEquals(capacity, successes.get());
        assertEquals(0, flightRepository.findByFlightNumber("AI202").orElseThrow().getAvailableSeats());
    }
}
