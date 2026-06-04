package com.gursangat.flightbooking.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returns201WithLocationOnSuccessfulBooking() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flightNumber\":\"AI404\",\"passengerName\":\"Alice\",\"seats\":2}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.bookingId").exists())
                .andExpect(jsonPath("$.flightNumber").value("AI404"))
                .andExpect(jsonPath("$.seats").value(2));
    }

    @Test
    void returns404ForUnknownFlight() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flightNumber\":\"ZZ999\",\"passengerName\":\"Bob\",\"seats\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns400ForInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flightNumber\":\"\",\"passengerName\":\"Bob\",\"seats\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replayWithSameIdempotencyKeyReturns200AndSameBooking() throws Exception {
        String body = "{\"flightNumber\":\"AI404\",\"passengerName\":\"Alice\",\"seats\":1}";

        // First request creates the booking -> 201 Created
        String created = mockMvc.perform(post("/api/bookings")
                        .header("Idempotency-Key", "abc-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String bookingId = JsonPath.read(created, "$.bookingId");

        // Same key replays the original booking -> 200 OK, identical bookingId
        mockMvc.perform(post("/api/bookings")
                        .header("Idempotency-Key", "abc-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId));
    }

    @Test
    void returns409WhenFlightIsFull() throws Exception {
        // AI303 has capacity 1 — first booking succeeds, second conflicts
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flightNumber\":\"AI303\",\"passengerName\":\"First\",\"seats\":1}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flightNumber\":\"AI303\",\"passengerName\":\"Second\",\"seats\":1}"))
                .andExpect(status().isConflict());
    }
}
