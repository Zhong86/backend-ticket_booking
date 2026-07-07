package com.example.ticketBooking.event;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Keyset (cursor) pagination: fetch events with id greater than the cursor,
     * ordered by id ascending. Far cheaper than OFFSET at scale because Postgres
     * can seek directly using the primary key index instead of scanning and
     * discarding N rows.
     *
     * Pass a Pageable of (0, limit) to bound the result set size.
     */
    @Query("""
        SELECT e FROM Event e
        WHERE (:cursor IS NULL OR e.id > :cursor)
        ORDER BY e.id ASC
        """)
    List<Event> findEventsAfterCursor(@Param("cursor") Long cursor, Pageable pageable);
}
