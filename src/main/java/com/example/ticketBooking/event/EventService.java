package com.example.ticketBooking.event;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class EventService {

  // Uses short TTL instead of explicit cache invalidation on write: a new event
  private static final Duration CACHE_TTL = Duration.ofSeconds(30);

  private final EventRepository eventRepository;
  private final RedisTemplate<String, Object> redisTemplate;

  public EventService(EventRepository eventRepository, RedisTemplate<String, Object> redisTemplate) {
    this.eventRepository = eventRepository;
    this.redisTemplate = redisTemplate;
  }

  public EventPageResult getEvents(Long cursor, int limit) {
    String cacheKey = buildCacheKey(cursor, limit);
  
    //try cache
    EventPageResult cached = (EventPageResult) redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) {
      return cached;
    }

    //cache miss
    List<Event> rows = eventRepository.findEventsAfterCursor(cursor, PageRequest.of(0, limit + 1));
    boolean hasMore = rows.size() > limit;
    List<Event> page = hasMore ? rows.subList(0, limit) : rows;
    Long nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;

    EventPageResult result = new EventPageResult(page, nextCursor, hasMore);

    // populate cache
    redisTemplate.opsForValue().set(cacheKey, result, CACHE_TTL);
    return result;
  }

  public String buildCacheKey(Long cursor, int limit) {
    return "events:page:cursor=%s:limit=%s".formatted(cursor == null ? "start" : cursor, limit);
  }

  public record EventPageResult(List<Event> events, Long nextCursor, boolean hasMore) {}
}
