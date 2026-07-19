package com.example.ticketBooking.booking;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketBooking.outbox.OutboxEvent;
import com.example.ticketBooking.outbox.OutboxEventRepository;
import com.example.ticketBooking.sharding.ShardRouter;

import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class BookingService {

  private static final Duration HOLD_DURATION = Duration.ofMinutes(5);

  private final SeatRepository seatRepository;
  private final BookingRepository bookingRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final SeatBookingAttemptService seatBookingAttemptService;
  private final ObjectMapper objectMapper;
  private final ShardRouter shardRouter;

  public BookingService(SeatRepository seatRepository, BookingRepository bookingRepository,
      SeatBookingAttemptService seatBookingAttemptService, OutboxEventRepository outboxEventRepository,
      ObjectMapper objectMapper, ShardRouter shardRouter) {
    this.seatRepository = seatRepository;
    this.bookingRepository = bookingRepository;
    this.outboxEventRepository = outboxEventRepository;
    this.seatBookingAttemptService = seatBookingAttemptService;
    this.objectMapper = objectMapper;
    this.shardRouter = shardRouter;
  }

  public Optional<Booking> bookSeatOnShard(Long showtimeId, String strategy, Long seatId, Long userId,
      String idempotencyKey) {
    return shardRouter.runOnShard(showtimeId, () -> "optimistic".equals(strategy)
        ? bookSeatOptimistic(seatId, userId, idempotencyKey)
        : bookSeat(seatId, userId, idempotencyKey));
  }

  public Optional<Booking> bookSeatOptimistic(Long seatId, Long userId, String idempotencyKey) {
    String key = (idempotencyKey != null) ? idempotencyKey : UUID.randomUUID().toString();

    var existing = bookingRepository.findByIdempotencyKey(key);
    if (existing.isPresent()) {
      return existing;
    }

    Optional<Seat> seatOpt = seatRepository.findByIdNoLock(seatId); // plain read, no lock
    if (seatOpt.isEmpty() || !"AVAILABLE".equals(seatOpt.get().getStatus())) {
      return Optional.empty();
    }

    try {
      return Optional.of(seatBookingAttemptService.attemptOptimisticBooking(seatId, userId, key, HOLD_DURATION));
    } catch (SeatUnavailableException | org.springframework.orm.ObjectOptimisticLockingFailureException e) {
      return Optional.empty();
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      return bookingRepository.findByIdempotencyKey(key);
    }
  }

  @Transactional
  public Optional<Booking> bookSeat(Long seatId, Long userId, String idempotencyKey) {
    String key = (idempotencyKey != null) ? idempotencyKey : UUID.randomUUID().toString();

    var existing = bookingRepository.findByIdempotencyKey(key);
    if (existing.isPresent()) {
      return existing;
    }

    Optional<Seat> seatOpt = seatRepository.findById(seatId); // pessimistic lock acquired here
    if (seatOpt.isEmpty() || !"AVAILABLE".equals(seatOpt.get().getStatus())) {
      return Optional.empty();
    }

    Seat seat = seatOpt.get();
    seat.setStatus("HELD");
    seatRepository.save(seat);

    Booking booking = new Booking(seatId, userId, key);
    booking.markHeld(Instant.now().plus(HOLD_DURATION));

    try {
      return Optional.of(bookingRepository.save(booking));
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      return bookingRepository.findByIdempotencyKey(key);
    }
  }

  @Transactional
  public Optional<Booking> confirmBooking(Long id) {
    Optional<Booking> bookingOpt = bookingRepository.findById(id);
    if (bookingOpt.isEmpty())
      return Optional.empty();

    Booking booking = bookingOpt.get();
    if (!"HELD".equals(booking.getStatus()))
      return Optional.empty();

    booking.markConfirmed();
    bookingRepository.save(booking);

    seatRepository.findById(booking.getSeatId()).ifPresent(seat -> {
      seat.setStatus("BOOKED");
      seatRepository.save(seat);
    });

    OutboxEvent outboxEvent = new OutboxEvent(
        "BOOKING", booking.getId(), "booking.confirmed", "{}");
    outboxEvent = outboxEventRepository.save(outboxEvent);
    outboxEvent.setPayload(buildConfirmationPayload(outboxEvent, booking));
    outboxEventRepository.save(outboxEvent);

    return Optional.of(booking);
  }

  public String buildConfirmationPayload(OutboxEvent outboxEvent, Booking booking) {
    try {
      Map<String, String> data = Map.of(
          "userId", String.valueOf(booking.getUserId()),
          "bookingId", String.valueOf(booking.getId()),
          "seatId", String.valueOf(booking.getSeatId()),
          "email", "test@gmail.com");
      Map<String, Object> event = Map.of(
          "eventId", String.valueOf(outboxEvent.getId()),
          "eventType", outboxEvent.getEventType(),
          "data", data);
      return objectMapper.writeValueAsString(event);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize outbox event payload.");
    }
  }
}
