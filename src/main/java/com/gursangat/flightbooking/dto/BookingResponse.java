package com.gursangat.flightbooking.dto;

import com.gursangat.flightbooking.model.Booking;

import java.time.Instant;

public class BookingResponse {

    private final String bookingId;
    private final String flightNumber;
    private final String passengerName;
    private final int seats;
    private final Instant createdAt;

    public BookingResponse(Booking booking) {
        this.bookingId = booking.getId();
        this.flightNumber = booking.getFlightNumber();
        this.passengerName = booking.getPassengerName();
        this.seats = booking.getSeats();
        this.createdAt = booking.getCreatedAt();
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public int getSeats() {
        return seats;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
