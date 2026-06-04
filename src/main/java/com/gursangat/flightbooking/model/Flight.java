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

    public synchronized int getBookedSeats() {
        return bookedSeats;
    }

    public synchronized int getAvailableSeats() {
        return capacity - bookedSeats;
    }

    /**
     * Atomically reserves the requested number of seats if enough are available.
     * Performing the check-and-reserve under the flight's intrinsic lock makes
     * this safe against concurrent booking requests, which is what actually
     * prevents overbooking — a check followed by a separate write would let two
     * callers both pass the check and oversell the flight.
     *
     * @return true if the seats were reserved, false if there was not enough room
     */
    public synchronized boolean reserve(int seats) {
        if (seats > capacity - bookedSeats) {
            return false;
        }
        bookedSeats += seats;
        return true;
    }
}
