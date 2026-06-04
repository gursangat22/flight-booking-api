package com.gursangat.flightbooking.repository;

import com.gursangat.flightbooking.model.Flight;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of flights. Since there is no flight search or creation API,
 * a small set of flights is seeded on startup. Clients book against these
 * known flight numbers.
 */
@Repository
public class FlightRepository {

    private final Map<String, Flight> flights = new ConcurrentHashMap<>();

    @PostConstruct
    void seed() {
        save(new Flight("AI101", 3));
        save(new Flight("AI202", 50));
        save(new Flight("AI303", 1));
        save(new Flight("AI404", 180));
    }

    public void save(Flight flight) {
        flights.put(flight.getFlightNumber(), flight);
    }

    public Optional<Flight> findByFlightNumber(String flightNumber) {
        return Optional.ofNullable(flights.get(flightNumber));
    }
}
