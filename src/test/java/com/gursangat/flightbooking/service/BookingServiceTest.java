package com.gursangat.flightbooking.service;

import com.gursangat.flightbooking.exception.FlightNotFoundException;
import com.gursangat.flightbooking.exception.SeatsUnavailableException;
import com.gursangat.flightbooking.model.Booking;
import com.gursangat.flightbooking.model.Flight;
import com.gursangat.flightbooking.repository.BookingRepository;
import com.gursangat.flightbooking.repository.FlightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingServiceTest {

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        FlightRepository flightRepository = new FlightRepository();
        flightRepository.save(new Flight("AI101", 3));
        flightRepository.save(new Flight("AI202", 50));
        flightRepository.save(new Flight("AI303", 1));
        bookingService = new BookingService(flightRepository, new BookingRepository());
    }

    @Test
    void booksSeatsOnAKnownFlight() {
        Booking booking = bookingService.book("AI202", "Alice", 2);

        assertNotNull(booking.getId());
        assertEquals("AI202", booking.getFlightNumber());
        assertEquals(2, booking.getSeats());
    }

    @Test
    void rejectsUnknownFlight() {
        assertThrows(FlightNotFoundException.class,
                () -> bookingService.book("ZZ999", "Bob", 1));
    }

    @Test
    void rejectsOverbooking() {
        // AI303 has capacity 1
        bookingService.book("AI303", "Carol", 1);

        assertThrows(SeatsUnavailableException.class,
                () -> bookingService.book("AI303", "Dave", 1));
    }

    @Test
    void fillsFlightExactlyToCapacity() {
        // AI101 has capacity 3
        bookingService.book("AI101", "P1", 3);

        assertThrows(SeatsUnavailableException.class,
                () -> bookingService.book("AI101", "P2", 1));
    }
}
