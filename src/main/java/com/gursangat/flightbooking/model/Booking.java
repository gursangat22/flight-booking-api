package com.gursangat.flightbooking.model;

import java.time.Instant;

/**
 * A confirmed booking of one or more seats on a flight.
 */
public class Booking {

    private final String id;
    private final String flightNumber;
    private final String passengerName;
    private final int seats;
    private final Instant createdAt;

    public Booking(String id, String flightNumber, String passengerName, int seats, Instant createdAt) {
        this.id = id;
        this.flightNumber = flightNumber;
        this.passengerName = passengerName;
        this.seats = seats;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
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
