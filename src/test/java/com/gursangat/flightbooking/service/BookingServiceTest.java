package com.gursangat.flightbooking.service;

import com.gursangat.flightbooking.exception.FlightNotFoundException;
import com.gursangat.flightbooking.exception.SeatsUnavailableException;
import com.gursangat.flightbooking.model.Booking;
import com.gursangat.flightbooking.model.Flight;
import com.gursangat.flightbooking.repository.BookingRepository;
import com.gursangat.flightbooking.repository.FlightRepository;
import com.gursangat.flightbooking.repository.IdempotencyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
