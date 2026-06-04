package com.gursangat.flightbooking.model;

/**
 * A flight with a fixed seating capacity. Bookings increase the number of
 * booked seats; the flight is full when bookedSeats == capacity.
 */
public class Flight {

    private final String flightNumber;
    private final int capacity;
    private int bookedSeats;

    public Flight(String flightNumber, int capacity) {
        this.flightNumber = flightNumber;
        this.capacity = capacity;
        this.bookedSeats = 0;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getBookedSeats() {
        return bookedSeats;
    }

    public void setBookedSeats(int bookedSeats) {
        this.bookedSeats = bookedSeats;
    }

    public int getAvailableSeats() {
        return capacity - bookedSeats;
    }
}
