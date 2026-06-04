package com.gursangat.flightbooking.controller;

import com.gursangat.flightbooking.dto.BookingRequest;
import com.gursangat.flightbooking.dto.BookingResponse;
import com.gursangat.flightbooking.service.BookingResult;
import com.gursangat.flightbooking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody BookingRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        BookingResult result = bookingService.book(
                request.getFlightNumber(),
                request.getPassengerName(),
                request.getSeats(),
                idempotencyKey);

        BookingResponse response = new BookingResponse(result.booking());
        URI location = URI.create("/api/bookings/" + result.booking().getId());

        // 201 when this request created the booking; 200 when it replayed an
        // existing one matched by its idempotency key (no new seats consumed).
        if (result.created()) {
            return ResponseEntity.created(location).body(response);
        }
        return ResponseEntity.ok().location(location).body(response);
    }
}
