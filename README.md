# Ticket Booking System

A practice backend project for **Amazon (AWS) Backend Internship** prep — built to cover backend engineering fundamentals, data structures & algorithms, and system design concepts through one cohesive, incrementally-built application.

Think Ticketmaster/BookMyShow: browse events, view seat maps, book a seat, get a confirmation. Simple to describe, deep enough to require real concurrency control, caching, async processing, and rate limiting.

## Build phases
### Phase 1 — Catalog & browsing
- `events`, `venues`, `showtimes` tables
- `GET /events` with **cursor-based (keyset) pagination**
- Redis cache-aside for popular event listings

### Phase 2 — Seat inventory & locking
- `seats` table (`id`, `showtime_id`, `row`, `col`, `status`)
- `POST /bookings` using `SELECT ... FOR UPDATE` (pessimistic) or a `version` column (optimistic locking)
- Load test: fire 50 concurrent requests at the same seat, prove exactly one succeeds

### Phase 3 — Idempotency & holds
- `Idempotency-Key` header so retried requests don't double-book
- Temporary "hold" state (5 min) while payment is pending, auto-released via scheduled job or Redis `EXPIRE`

### Phase 4 — Async confirmation
- On successful payment, publish an event to a queue
- Notification service consumes the queue and sends the confirmation (reuses the earlier notification system)

### Phase 5 — Rate limiting & abuse protection
- Apply rate limiting specifically to the booking endpoint (limit bookings per user per minute)

### Phase 6 — Waitlist
- Sold-out show → users join a waitlist (queue or min-heap by join time/priority)
- Seat released → pop next in line, notify them

## Concepts covered

| Phase | Backend concept | DSA | System design |
|---|---|---|---|
| 1 | Caching, pagination | — | Cache invalidation strategy |
| 2 | Transactions, locking | — | Race conditions, consistency |
| 3 | Idempotency, TTL | — | Exactly-once semantics |
| 4 | Async processing, queues | Queue processing | Decoupling, eventual consistency |
| 5 | Rate limiting | Sliding window | Abuse prevention at scale |
| 6 | — | Heap / priority queue | Fairness, notification fanout |

## API endpoints (planned)

| Method | Endpoint | Description |
|---|---|---|
| GET | `/events?cursor={cursor}&limit={n}` | Browse events, cursor-paginated |
| GET | `/events/{id}/showtimes` | List showtimes for an event |
| GET | `/showtimes/{id}/seats` | Seat map with availability |
| POST | `/bookings` | Reserve a seat (idempotent) |
| POST | `/bookings/{id}/confirm` | Confirm payment, trigger notification |
| DELETE | `/bookings/{id}` | Cancel a hold or booking |
| POST | `/waitlist` | Join waitlist for a sold-out showtime |

## Getting started

```bash
# clone and enter the project
git clone <repo-url>
cd ticket-booking-system

# start Postgres and Redis (docker-compose recommended)
docker-compose up -d

# run the app
./mvnw spring-boot:run
```

## Testing concurrency (Phase 2 checkpoint)

A key deliverable of this project is proving your locking strategy works. Include a load test script (e.g. using a simple script with parallel HTTP requests, or a JMeter/k6 plan) that:

1. Fires N concurrent booking requests for the same seat
2. Asserts exactly 1 succeeds and N-1 receive a "seat unavailable" response
3. Confirms no data corruption (seat status is consistent after the run)

## Roadmap / stretch goals

- [ ] Swap in-memory queue for Amazon SQS
- [ ] Add distributed lock via Redis (`SET NX`) as an alternative to Postgres row locks
- [ ] Add consistent hashing if sharding seat inventory across nodes
- [ ] Add OpenAPI/Swagger docs
- [ ] Containerize with Docker and deploy to a free-tier cloud instance

## Notes
### Indexes
Creates a B-tree for values -> row locations, so database can jump to matching rows instead of checking every row in the table. Trades off write speed and storage for read speed. 
- Every index has to be updated on every write. If events has 5 indexes and a row is inserted, Postgres has to write 6 times. 
- Each index uses around 10 - 50% of the table's size. 
- Diminishing returns on low-cardinality columns. If a .status only has 4 options and 1 status is 40%, that wouldn't be that much faster compared to no index. 
