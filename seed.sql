-- ============================================================
-- Ticket Booking System — seeder.sql
-- Run after schema.sql. Populates enough data to exercise
-- pagination, seat locking, and the waitlist.
-- ============================================================

-- ---------------------------------------------------------
-- Venues
-- ---------------------------------------------------------
INSERT INTO venues (name, city) VALUES
    ('Madison Square Garden', 'New York'),
    ('The Fillmore', 'San Francisco'),
    ('Red Rocks Amphitheatre', 'Morrison');

-- ---------------------------------------------------------
-- Events (enough rows to test cursor pagination, e.g. limit=5)
-- ---------------------------------------------------------
INSERT INTO events (venue_id, name, description) VALUES
    (1, 'Rock Legends Live',        'A night of classic rock covers.'),
    (1, 'Comedy Night Special',      'Stand-up from touring comedians.'),
    (2, 'Indie Sundown Sessions',    'Local indie acts, acoustic sets.'),
    (2, 'Jazz & Blues Evening',      'Smooth jazz and blues quartet.'),
    (3, 'Sunset Symphony',           'Outdoor orchestral performance.'),
    (3, 'Electronic Nights',         'DJ sets under the stars.'),
    (1, 'Broadway Revival Tour',     'Musical theatre highlights.'),
    (2, 'Folk & Storytelling',       'Acoustic folk with spoken word.'),
    (3, 'Summer Rock Festival',      'Multi-band outdoor festival.'),
    (1, 'Classical Piano Recital',   'Solo piano performance.');

-- ---------------------------------------------------------
-- Showtimes (a couple per event)
-- ---------------------------------------------------------
INSERT INTO showtimes (event_id, starts_at) VALUES
    (1, now() + interval '7 days'),
    (1, now() + interval '8 days'),
    (2, now() + interval '10 days'),
    (3, now() + interval '5 days'),
    (4, now() + interval '12 days'),
    (5, now() + interval '20 days'),
    (6, now() + interval '14 days'),
    (7, now() + interval '30 days'),
    (8, now() + interval '9 days'),
    (9, now() + interval '25 days');

-- ---------------------------------------------------------
-- Seats: generate a small 5-row x 10-seat grid for showtime 1,
-- enough to run the concurrent-booking race-condition test on
-- a single seat (e.g. row A, seat 1).
-- ---------------------------------------------------------
INSERT INTO seats (showtime_id, seat_row, seat_number, status)
SELECT
    1 AS showtime_id,
    chr(64 + row_num) AS seat_row,   -- A, B, C, D, E
    seat_num,
    'AVAILABLE'
FROM generate_series(1, 5) AS row_num
CROSS JOIN generate_series(1, 10) AS seat_num;

-- Same seat grid for showtime 2, but mark a few as already BOOKED
-- so pagination/filtering by availability has something to exclude.
INSERT INTO seats (showtime_id, seat_row, seat_number, status)
SELECT
    2 AS showtime_id,
    chr(64 + row_num) AS seat_row,
    seat_num,
    CASE WHEN row_num = 1 AND seat_num <= 3 THEN 'BOOKED' ELSE 'AVAILABLE' END
FROM generate_series(1, 5) AS row_num
CROSS JOIN generate_series(1, 10) AS seat_num;

-- ---------------------------------------------------------
-- A couple of sample bookings for the already-BOOKED seats above
-- ---------------------------------------------------------
INSERT INTO bookings (seat_id, user_id, status, idempotency_key)
SELECT id, 101, 'CONFIRMED', 'seed-key-' || id
FROM seats
WHERE showtime_id = 2 AND status = 'BOOKED';

-- ---------------------------------------------------------
-- A sample waitlist entry (for testing Phase 6 once a showtime sells out)
-- ---------------------------------------------------------
INSERT INTO waitlist_entries (showtime_id, user_id) VALUES
    (2, 202),
    (2, 303);
