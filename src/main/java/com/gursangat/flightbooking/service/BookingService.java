package com.gursangat.flightbooking.service;

import com.gursangat.flightbooking.exception.FlightNotFoundException;
import com.gursangat.flightbooking.exception.SeatsUnavailableException;
import com.gursangat.flightbooking.model.Booking;
import com.gursangat.flightbooking.model.Flight;
import com.gursangat.flightbooking.repository.BookingRepository;
import com.gursangat.flightbooking.repository.FlightRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class BookingService {

    private final FlightRepository flightRepository;
    private final BookingRepository bookingRepository;

    public BookingService(FlightRepository flightRepository, BookingRepository bookingRepository) {
        this.flightRepository = flightRepository;
        this.bookingRepository = bookingRepository;
    }

    /**
     * Books the requested number of seats on a flight.
     *
     * @throws FlightNotFoundException    if the flight number is unknown
     * @throws SeatsUnavailableException  if there are not enough free seats
     */
    public Booking book(String flightNumber, String passengerName, int seats) {
        Flight flight = flightRepository.findByFlightNumber(flightNumber)
                .orElseThrow(() -> new FlightNotFoundException(flightNumber));

        if (flight.getAvailableSeats() < seats) {
            throw new SeatsUnavailableException(flightNumber, seats, flight.getAvailableSeats());
        }

        flight.setBookedSeats(flight.getBookedSeats() + seats);

        Booking booking = new Booking(
                UUID.randomUUID().toString(),
                flightNumber,
                passengerName,
                seats,
                Instant.now());

        return bookingRepository.save(booking);
    }
}
