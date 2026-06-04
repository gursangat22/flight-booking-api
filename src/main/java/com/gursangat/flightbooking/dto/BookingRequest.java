package com.gursangat.flightbooking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class BookingRequest {

    @NotBlank(message = "flightNumber is required")
    private String flightNumber;

    @NotBlank(message = "passengerName is required")
    private String passengerName;

    @Min(value = 1, message = "seats must be at least 1")
    private int seats;

    public String getFlightNumber() {
        return flightNumber;
    }

    public void setFlightNumber(String flightNumber) {
        this.flightNumber = flightNumber;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public void setPassengerName(String passengerName) {
        this.passengerName = passengerName;
    }

    public int getSeats() {
        return seats;
    }

    public void setSeats(int seats) {
        this.seats = seats;
    }
}
