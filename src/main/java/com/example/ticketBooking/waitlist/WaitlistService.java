package com.example.ticketBooking.waitlist;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketBooking.booking.Booking;
import com.example.ticketBooking.booking.BookingService;
import com.example.ticketBooking.booking.Seat;
import com.example.ticketBooking.booking.SeatRepository;
import com.example.ticketBooking.outbox.OutboxEvent;
import com.example.ticketBooking.outbox.OutboxEventRepository;
import com.example.ticketBooking.sharding.ShardRouter;

import tools.jackson.databind.ObjectMapper;

@Service
public class WaitlistService {

  private static final Logger log = LoggerFactory.getLogger(WaitlistService.class);
  private final WaitlistRepository waitlistRepository;
  private final SeatRepository seatRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final BookingService bookingService;
  private final ObjectMapper objectMapper;
  private final ShardRouter shardRouter;

  public WaitlistService(WaitlistRepository waitlistRepository, SeatRepository seatRepository,
      OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper, BookingService bookingService, ShardRouter shardRouter) {
    this.waitlistRepository = waitlistRepository;
    this.seatRepository = seatRepository;
    this.outboxEventRepository = outboxEventRepository;
    this.bookingService = bookingService;
    this.objectMapper = objectMapper;
    this.shardRouter = shardRouter;
  }

  public WaitlistEntry joinWaitlist(Long showtimeId, Long userId) {
    long available = seatRepository.countByShowtimeIdAndStatus(showtimeId, "AVAILABLE");
    if (available > 0) {
      throw new IllegalStateException("Showtime " + showtimeId + " still has seats available");
    }
    return attemptJoin(showtimeId, userId);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public WaitlistEntry attemptJoin(Long showtimeId, Long userId) {
    try {
      return waitlistRepository.saveAndFlush(new WaitlistEntry(showtimeId, userId));
    } catch (DataIntegrityViolationException e) {
      return waitlistRepository.findByShowtimeIdAndUserId(showtimeId, userId).orElseThrow(() -> e);
    }
  }

  public void promoteWaitlist() {
    for (String shard : shardRouter.allShards()) {
      shardRouter.runOnNamedShard(shard, () -> {
        promoteWaitlistOnCurrentShard();
        return null;
      });
    }
  }

  private void promoteWaitlistOnCurrentShard() {
    List<WaitlistEntry> waiters = waitlistRepository.findByNotifiedAtIsNullOrderByShowtimeIdAscJoinedAtAsc();
    if (waiters.isEmpty()) {
      log.info("Waiters empty");
      return;
    }

    List<Long> showtimeIds = waiters.stream()
        .map(WaitlistEntry::getShowtimeId)
        .distinct()
        .toList();

    List<Seat> availableSeats = seatRepository
        .findByShowtimeIdInAndStatusOrderByShowtimeIdAscIdAsc(showtimeIds, "AVAILABLE");

    Map<Long, List<WaitlistEntry>> waitersByShowtime = waiters.stream()
        .collect(Collectors.groupingBy(WaitlistEntry::getShowtimeId, LinkedHashMap::new, Collectors.toList()));

    Map<Long, List<Seat>> seatsByShowtime = availableSeats.stream()
        .collect(Collectors.groupingBy(Seat::getShowtimeId, LinkedHashMap::new, Collectors.toList()));

    for (Long showtimeId : showtimeIds) {
      List<WaitlistEntry> showtimeWaiters = waitersByShowtime.get(showtimeId);
      List<Seat> showtimeSeats = seatsByShowtime.getOrDefault(showtimeId, List.of());

      int pairCount = Math.min(showtimeWaiters.size(), showtimeSeats.size());
      for (int i = 0; i < pairCount; i++) {
        promoteOne(showtimeWaiters.get(i), showtimeSeats.get(i).getId());
      }
    }
  }

  private void promoteOne(WaitlistEntry entry, Long seatId) {
    String idempotencyKey = "waitlist-" + entry.getId();

    var booking = bookingService.bookSeatOptimistic(seatId, entry.getUserId(), idempotencyKey);
    if (booking.isEmpty()) {
      // lost the race for this seat (or the seat flipped state between our
      // count and now) — leave the entry unnotified, next scheduler tick retries
      return;
    }

    entry.setNotifiedAt(Instant.now());
    waitlistRepository.save(entry);
    publishWaitlistNotification(entry, booking.get());
  }

  private void publishWaitlistNotification(WaitlistEntry entry, Booking booking) {
    OutboxEvent event = new OutboxEvent("WAITLIST", entry.getId(), "waitlist.seat_held", "{}");
    event = outboxEventRepository.save(event);
    event.setPayload(buildPayload(event, entry, booking));
    outboxEventRepository.save(event);
  }

  private String buildPayload(OutboxEvent event, WaitlistEntry entry, Booking booking) {
    try {
      Map<String, String> data = Map.of(
          "userId", String.valueOf(entry.getUserId()),
          "showtimeId", String.valueOf(entry.getShowtimeId()),
          "bookingId", String.valueOf(booking.getId()),
          "heldUntil", String.valueOf(booking.getHeldUntil()),
          "email", "test@gmail.com");
      Map<String, Object> payload = Map.of(
          "eventId", String.valueOf(event.getId()),
          "eventType", event.getEventType(),
          "data", data);
      return objectMapper.writeValueAsString(payload);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize waitlist outbox payload.");
    }
  }
}
