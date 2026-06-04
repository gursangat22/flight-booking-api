# Flight Booking API

A small REST API for a flight ticket booking system, built with **Spring Boot 3** and **Java 17**.

It does one thing: book seats on a known flight, without ever overbooking it.

## How this was built (AI prompts & process)

This was built AI-first, as the exercise expects.

- **Step 1 (AI):** every iteration was driven by an AI prompt, and **each prompt is
  recorded in its git commit message** — run `git log` to see all of them, in order.
- **Step 2 (manual):** the manual improvements are the commits titled
  *"Manual improvements (Step 2) …"* — these explain, in plain language, what I
  changed by hand, why, and what I'd still fix with more time.

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
| Booking replayed (same `Idempotency-Key`, same body) | `200 OK` (+ `Location` header) |
| Invalid body (missing fields, `seats < 1`) | `400 Bad Request` |
| Flight number unknown           | `404 Not Found`    |
| Not enough seats left           | `409 Conflict`     |
| Same `Idempotency-Key` reused with a *different* body | `422 Unprocessable Entity` |

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

## Scaling beyond a single instance (when to add Redis)

Per the brief this is a **single instance with in-memory storage**, so no Redis or
database is used — and deliberately so. Both correctness guarantees above rely on
**in-process** primitives that only hold inside one JVM:

- no-overbooking uses each `Flight`'s intrinsic lock;
- idempotency uses an in-process `ConcurrentHashMap`.

The moment you run **more than one instance behind a load balancer**, those
in-process guarantees no longer hold across the fleet: two instances could each
sell the "last" seat, and the same `Idempotency-Key` could create one booking per
instance. That is the point at which I would introduce a shared, atomic store —
**Redis** is the natural fit:

| Concern            | Single instance (current)        | Multi-instance (would add Redis)                          |
|--------------------|----------------------------------|-----------------------------------------------------------|
| No overbooking     | `synchronized Flight.reserve()`  | Atomic seat counter via Redis `DECRBY` / Lua script, or a DB row with a `CHECK`/conditional update |
| Idempotency        | in-process `computeIfAbsent`     | `SET <key> <bookingId> NX EX <ttl>` so the first writer wins cluster-wide, with a TTL for cleanup |
| Cross-step locking | not needed                       | Redis distributed lock (Redlock) only if multiple keys must be coordinated atomically |

Redis would also give the idempotency store a natural **TTL/eviction** (the
in-memory map currently grows unbounded). Until the service actually needs to
scale out, adding Redis would be unnecessary infrastructure and extra failure
modes — so it is intentionally left out here and called out as the first thing to
add when scaling past one instance.

## Project structure

```
controller/   REST layer (BookingController)
service/      Booking business logic + no-overbooking rule + idempotency (BookingResult)
repository/   In-memory stores (FlightRepository, BookingRepository, IdempotencyStore)
model/        Domain models (Flight, Booking)
dto/          Request/response payloads with validation
exception/    Custom exceptions + global handler mapping to HTTP status codes
```

## Design notes: SOLID principles & patterns

The code is small on purpose, but it follows a few well-known principles and
patterns. They are listed here with the exact place in the code they show up, so
each one is concrete rather than a buzzword.

### SOLID

- **Single Responsibility (S)** — every class has one reason to change:
  `BookingController` only does HTTP (parse request, choose status code),
  `BookingService` only does business logic, the `*Repository` classes only store
  data, `Flight`/`Booking` only hold domain state, the DTOs only describe the wire
  format, and `GlobalExceptionHandler` only maps exceptions to HTTP responses.
- **Open/Closed (O)** — `GlobalExceptionHandler` is open for extension, closed for
  modification: handling a new error type means *adding* an `@ExceptionHandler`
  method, not editing existing ones. The same is true of adding a new endpoint or
  a new repository.
- **Liskov Substitution (L)** — `FlightNotFoundException` and
  `SeatsUnavailableException` are true subtypes of `RuntimeException` and behave
  correctly anywhere a `RuntimeException` is expected (e.g. Spring's exception
  handling), so substituting them changes nothing for the caller.
- **Interface Segregation (I)** — clients never depend on more than they need:
  request and response are **separate DTOs** (`BookingRequest` vs
  `BookingResponse`) instead of one fat object, so the input contract and the
  output contract can evolve independently.
- **Dependency Inversion (D)** — `BookingService` and `BookingController` receive
  their collaborators through **constructor injection** (wired by Spring) rather
  than constructing them, so a high-level class never news-up its dependencies and
  collaborators can be swapped (e.g. with mocks in tests).

> Honest note: I deliberately did **not** introduce interfaces in front of the
> repositories. For a service this size that would be abstraction for its own sake
> (YAGNI). If a second implementation were ever needed (e.g. a Redis-backed store),
> extracting a `FlightRepository` interface at that point is a one-minute change and
> the constructor injection above already makes the swap painless.

### Design patterns

- **Layered architecture** — `controller → service → repository → model`. Each
  layer only talks to the one below it; HTTP concerns never leak into the domain.
- **Repository pattern** — `FlightRepository`, `BookingRepository` and
  `IdempotencyStore` hide *how* data is stored behind simple methods, so today's
  in-memory maps could become a database tomorrow without touching the service.
- **DTO pattern** — `BookingRequest`/`BookingResponse` decouple the public API
  shape from the internal `Booking` model (and carry the validation rules).
- **Dependency Injection / IoC** — Spring constructs and wires the beans.
- **Information Expert + "Tell, Don't Ask"** — the no-overbooking invariant lives
  in `Flight.reserve(seats)`, the object that actually owns `capacity` and
  `bookedSeats`. The service *tells* the flight to reserve rather than reading its
  fields and deciding externally, which is also what makes the operation atomic.
- **Memoization for idempotency** — `IdempotencyStore.computeIfAbsent` computes a
  booking once per key and returns the cached result on repeats; this is the
  pattern that gives the at-most-once guarantee.
- **Immutable value objects** — `BookingResult` is a `record`, and `Booking`'s
  fields are `final`, so results can't be mutated after creation.

## What I'd improve with more time

The current code intentionally stays simple and within the brief (single instance,
in-memory, booking only). These are the things I consciously left out and *why*,
along with how I would approach each one — in rough priority order.

### 1. Persistence (durability)
**What's there now:** flights, bookings and idempotency keys live in
`ConcurrentHashMap`s, so everything is lost on restart.
**Why it's fine for this exercise:** the brief explicitly says in-memory only, and
it keeps the project runnable with zero setup.
**What I'd do:** put bookings and seat inventory in a database (e.g. Postgres). The
no-overbooking rule would then be enforced by the database itself — either a row
with a conditional `UPDATE ... WHERE booked_seats + :n <= capacity` (and check the
affected-row count) or a `CHECK` constraint — instead of an in-memory lock. That
also makes the rule correct even if the app restarts mid-flight.

### 2. Idempotency-store hardening
**What's there now:** an `Idempotency-Key` maps to a booking in an in-process map.
**What's already handled:** reusing a key with a *different* request body is
rejected with **422 Unprocessable Entity** (the store keeps a fingerprint of the
original request), so a key can't be accidentally reused for a different booking.
**Remaining gap:** the map **grows forever** — there's no expiry. I'd add a
**TTL/eviction** (keys only need to live as long as a client might retry, e.g. 24h)
and, when multi-instance, move it to a shared store — see *Scaling beyond a single
instance*.

### 3. Booking lifecycle (more of the domain)
**What's there now:** you can only create a booking.
**What I'd add:** the rest of the natural lifecycle — **cancel a booking** (which
frees the seats back to the flight) and a **GET /api/bookings/{id}** to fetch one.
The brief said retrieval isn't required, so I left it out, but in a real system
cancellation is what makes the seat count meaningful over time.

### 4. Concurrency model (only matters at higher scale)
**What's there now:** each `Flight` is guarded by its own intrinsic lock, which is
**correct** but serialises all bookings *for the same flight*.
**Why it's fine now:** for a single instance and realistic traffic this is more than
enough, and it's the simplest thing that's provably correct.
**What I'd do if it became a bottleneck:** replace the lock with an atomic seat
counter (e.g. `AtomicInteger` with a compare-and-set reserve loop) so bookings for
the same flight don't block each other; and across multiple instances, move the
invariant to Redis/DB as described in the scaling section.

### 5. API & developer experience
- **OpenAPI/Swagger** docs so the contract is browsable and testable from a UI.
- **Seat-level inventory** (specific seat numbers) instead of just a count, if the
  business needs seat selection.
- **Consistent error model** — the error body is already structured; I'd formalise
  it (e.g. RFC 7807 `application/problem+json`).

### 6. Observability & operations
**What's there now:** default Spring Boot logging only.
**What I'd add:** structured request logging, metrics (bookings created/rejected,
seats remaining per flight) via Micrometer/Actuator, and tracing — so in production
you can answer "why did this booking fail?" and "which flights are filling up?".

> Summary for discussion: the design deliberately favours **correctness and clarity
> over features**. The two hard parts of a booking system — *not overbooking* and
> *not double-charging on retries* — are both solved and tested. Everything above is
> about durability, scale, and breadth of the domain, none of which the brief asked
> for but all of which I can reason about.
