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
- **No overbooking, even under concurrency.** A booking is rejected if the
  requested seats exceed the remaining capacity, and the check is safe when two
  passengers book the last seats at the same time (see *Concurrency & idempotency*).
- **Idempotent bookings.** Clients can send an `Idempotency-Key` header so a
  retried or double-submitted request does not create a duplicate booking.
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

Optional header:

| Header            | Purpose                                                        |
|-------------------|----------------------------------------------------------------|
| `Idempotency-Key` | A client-generated key. Repeating a request with the same key returns the original booking instead of creating a new one. |

| Outcome                         | HTTP status        |
|---------------------------------|--------------------|
| Booking created                 | `201 Created` (+ `Location` header) |
| Booking replayed (same `Idempotency-Key`) | `200 OK` (+ `Location` header) |
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

Idempotent retry — sending the same `Idempotency-Key` twice books once:

```bash
# First call -> 201 Created
curl -i -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 11111111-2222-3333-4444-555555555555" \
  -d '{"flightNumber":"AI202","passengerName":"Alice","seats":1}'

# Same key again -> 200 OK, same bookingId, no extra seat consumed
curl -i -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 11111111-2222-3333-4444-555555555555" \
  -d '{"flightNumber":"AI202","passengerName":"Alice","seats":1}'
```

## Concurrency & idempotency

Two correctness concerns are handled explicitly:

- **Two passengers booking the last seats at the same time** must not overbook.
  The seat check-and-reserve is a single atomic operation guarded by the flight's
  intrinsic lock (`Flight.reserve(seats)`), so concurrent bookings are serialised
  per flight and the capacity invariant always holds. Proven by
  `doesNotOverbookUnderConcurrentRequests` (200 parallel bookings on a 50-seat
  flight → exactly 50 succeed).
- **The same passenger submitting twice** (retry, timeout, double click) must not
  be charged twice. When an `Idempotency-Key` is supplied the booking is created
  at most once for that key, via `ConcurrentHashMap.computeIfAbsent` — even two
  *simultaneous* requests with the same key produce a single booking and consume
  seats once. Proven by `concurrentRequestsWithSameKeyCreateExactlyOneBooking`
  (50 parallel requests, one key → exactly one booking, one seat).

Without an `Idempotency-Key`, each request is treated as a new booking (the key
is opt-in).

## Project structure

```
controller/   REST layer (BookingController)
service/      Booking business logic + no-overbooking rule + idempotency (BookingResult)
repository/   In-memory stores (FlightRepository, BookingRepository, IdempotencyStore)
model/        Domain models (Flight, Booking)
dto/          Request/response payloads with validation
exception/    Custom exceptions + global handler mapping to HTTP status codes
```

## What I'd improve with more time

- **Persistence** — swap the in-memory maps for a real datastore so bookings
  survive a restart. The idempotency invariant would then move to a unique
  constraint / atomic upsert instead of an in-process map.
- **Idempotency-store hardening** — the key store currently grows unbounded and
  lives only in this instance. With more time: add a TTL / eviction policy,
  validate that the same key is not reused with a *different* request body
  (return `422`), and back it with a shared store so it holds across instances.
- **Booking lifecycle** — cancellation (freeing seats) and a fetch-by-id endpoint.
- **Lock granularity** — the per-flight intrinsic lock is correct for a single
  instance but serialises bookings for a given flight; a striped lock or an atomic
  counter would scale better under heavy contention, and a distributed lock would
  be required across multiple instances.
- **API polish** — OpenAPI/Swagger docs, pagination, and seat-level (not just
  count-level) inventory.
- **Observability** — structured logging, metrics, and tracing around bookings.
