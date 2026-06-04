package com.gursangat.flightbooking.exception;

public class SeatsUnavailableException extends RuntimeException {

    public SeatsUnavailableException(String flightNumber, int requested, int available) {
        super("Cannot book " + requested + " seat(s) on flight " + flightNumber
                + "; only " + available + " seat(s) available");
    }
}
