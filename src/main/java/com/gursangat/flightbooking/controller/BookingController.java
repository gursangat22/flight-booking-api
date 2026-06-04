package com.gursangat.flightbooking.controller;

import com.gursangat.flightbooking.dto.BookingRequest;
import com.gursangat.flightbooking.dto.BookingResponse;
import com.gursangat.flightbooking.model.Booking;
import com.gursangat.flightbooking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody BookingRequest request) {
        Booking booking = bookingService.book(
                request.getFlightNumber(),
                request.getPassengerName(),
                request.getSeats());

        BookingResponse response = new BookingResponse(booking);
        URI location = URI.create("/api/bookings/" + booking.getId());
        return ResponseEntity.created(location).body(response);
    }
}
