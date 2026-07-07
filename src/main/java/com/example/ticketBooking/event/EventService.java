package com.example.ticketBooking.event;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public EventPageResult getEvents(Long cursor, int limit) {
        // Fetch one extra row to know whether another page exists, without a
        // second COUNT query.
        List<Event> rows = eventRepository.findEventsAfterCursor(cursor, PageRequest.of(0, limit + 1));

        boolean hasMore = rows.size() > limit;
        List<Event> page = hasMore ? rows.subList(0, limit) : rows;
        Long nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;

        return new EventPageResult(page, nextCursor, hasMore);
    }

    public record EventPageResult(List<Event> events, Long nextCursor, boolean hasMore) {}
}
