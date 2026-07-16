-- ============================================================
-- Ticket Booking System — schema.sql
-- Postgres 15+
-- ============================================================
BEGIN;

-- ---------------------------------------------------------
-- Venues
-- ---------------------------------------------------------
CREATE TABLE venues (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    city        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------
-- Events (Phase 1: catalog & browsing)
-- ---------------------------------------------------------
CREATE TABLE events (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venue_id    BIGINT NOT NULL REFERENCES venues(id),
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Supports keyset (cursor) pagination ordered by id.
CREATE INDEX idx_events_id ON events (id);
CREATE INDEX idx_events_venue_id ON events (venue_id);

-- ---------------------------------------------------------
-- Showtimes
-- ---------------------------------------------------------
CREATE TABLE showtimes (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id    BIGINT NOT NULL REFERENCES events(id),
    starts_at   TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_showtimes_event_id ON showtimes (event_id);
CREATE INDEX idx_showtimes_starts_at ON showtimes (starts_at);

-- ---------------------------------------------------------
-- Seats (Phase 2: inventory & locking)
-- One row per physical seat per showtime.
-- `version` supports optimistic locking; row-level `SELECT ... FOR UPDATE`
-- is the alternative pessimistic approach — pick one per experiment.
-- ---------------------------------------------------------
CREATE TABLE seats (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    showtime_id  BIGINT NOT NULL REFERENCES showtimes(id),
    seat_row     VARCHAR(5) NOT NULL,
    seat_number  INT NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
                 CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED')),
    version      INT NOT NULL DEFAULT 0,
    UNIQUE (showtime_id, seat_row, seat_number)
);

CREATE INDEX idx_seats_showtime_id ON seats (showtime_id);
CREATE INDEX idx_seats_showtime_status ON seats (showtime_id, status);

-- ---------------------------------------------------------
-- Bookings (Phase 2/3: locking, idempotency, holds)
-- ---------------------------------------------------------
CREATE TABLE bookings (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seat_id          BIGINT NOT NULL REFERENCES seats(id),
    user_id          BIGINT NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING', 'HELD', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    idempotency_key  VARCHAR(100) NOT NULL,
    held_until       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (idempotency_key)
);

CREATE INDEX idx_bookings_user_id ON bookings (user_id);
CREATE INDEX idx_bookings_seat_id ON bookings (seat_id);
CREATE INDEX idx_bookings_held_until ON bookings (held_until)
    WHERE status = 'HELD';

-- ---------------------------------------------------------
-- Waitlist entries (Phase 6)
-- ---------------------------------------------------------
CREATE TABLE waitlist_entries (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    showtime_id  BIGINT NOT NULL REFERENCES showtimes(id),
    user_id      BIGINT NOT NULL,
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    notified_at  TIMESTAMPTZ,
    UNIQUE (showtime_id, user_id)
);

CREATE INDEX idx_waitlist_showtime_joined ON waitlist_entries (showtime_id, joined_at);

-- --------------------------------------------------------
-- Outbox events
-- --------------------------------------------------------
CREATE TABLE outbox_events (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,   -- e.g. 'BOOKING'
    aggregate_id   BIGINT NOT NULL,        -- e.g. booking.id
    event_type     VARCHAR(50) NOT NULL,   -- e.g. 'booking.confirmed'
    payload        TEXT NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_outbox_aggregate_id ON outbox_events (aggregate_id);

COMMIT;
