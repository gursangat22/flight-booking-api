# Flight Booking API

A small REST API for a flight ticket booking system, built with **Spring Boot 3** and **Java 17**.

It does one thing: book seats on a known flight, without ever overbooking it.

## Scope & assumptions

The brief intentionally left the detailed spec open, so the following reasonable
business decisions were made to fit the time box:

- **No flight search / destinations.** Clients already know the flight number.
- **No auth, rate limiting, or distributed-systems concerns** — single instance.
- **In-memory storage only.** A small set of flights is seeded on startup; all
  state is lost on restart.
- **No overbooking.** A booking is rejected if the requested seats exceed the
  remaining capacity.
- **Booking only.** There is no endpoint to list or fetch bookings (per the brief).
- A single booking can reserve **multiple seats** (`seats >= 1`).

### Seeded flights

| Flight number | Capacity |
|---------------|----------|
| `AI101`       | 3        |
| `AI202`       | 50       |
| `AI303`       | 1        |
| `AI404`       | 180      |

## How to run

Requires **Java 17+**. Maven is *not* required — the project ships with the Maven wrapper.

```bash
# from the project root
./mvnw spring-boot:run
```

The service starts on **http://localhost:8080**.

To build a runnable jar instead:

```bash
./mvnw clean package
java -jar target/flight-booking-api-0.0.1-SNAPSHOT.jar
```

Run the tests:

```bash
./mvnw test
```

## API

### Book seats

`POST /api/bookings`

Request body:

```json
{
  "flightNumber": "AI202",
  "passengerName": "Alice",
  "seats": 2
}
```

| Outcome                         | HTTP status        |
|---------------------------------|--------------------|
| Booking created                 | `201 Created` (+ `Location` header) |
| Invalid body (missing fields, `seats < 1`) | `400 Bad Request` |
| Flight number unknown           | `404 Not Found`    |
| Not enough seats left           | `409 Conflict`     |

### Example requests

Successful booking:

```bash
curl -i -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" \
  -d '{"flightNumber":"AI202","passengerName":"Alice","seats":2}'
```

```
HTTP/1.1 201 Created
Location: /api/bookings/3f2a...

{
  "bookingId": "3f2a...",
  "flightNumber": "AI202",
  "passengerName": "Alice",
  "seats": 2,
  "createdAt": "2026-06-05T08:00:00Z"
}
```

Overbooking is rejected (`AI303` has only 1 seat):

```bash
curl -i -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" \
  -d '{"flightNumber":"AI303","passengerName":"Bob","seats":2}'
```

```
HTTP/1.1 409 Conflict

{
  "timestamp": "2026-06-05T08:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Cannot book 2 seat(s) on flight AI303; only 1 seat(s) available"
}
```

Unknown flight:

```bash
curl -i -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" \
  -d '{"flightNumber":"ZZ999","passengerName":"Bob","seats":1}'
```

```
HTTP/1.1 404 Not Found
```

## Project structure

```
controller/   REST layer (BookingController)
service/      Booking business logic + no-overbooking rule
repository/   In-memory stores (FlightRepository seeds flights, BookingRepository)
model/        Domain models (Flight, Booking)
dto/          Request/response payloads with validation
exception/    Custom exceptions + global handler mapping to HTTP status codes
```

## What I'd improve with more time

- **Persistence** — swap the in-memory maps for a real datastore so bookings
  survive a restart.
- **Idempotency** — accept an idempotency key so a retried request doesn't create
  a duplicate booking.
- **Booking lifecycle** — cancellation (freeing seats) and a fetch-by-id endpoint.
- **Richer concurrency model** — the current per-flight lock is simple and correct
  for a single instance; a striped lock or an atomic reserve would scale better
  under heavy contention, and a distributed lock would be needed for multiple
  instances.
- **API polish** — OpenAPI/Swagger docs, pagination, and seat-level (not just
  count-level) inventory.
- **Observability** — structured logging, metrics, and tracing around bookings.
