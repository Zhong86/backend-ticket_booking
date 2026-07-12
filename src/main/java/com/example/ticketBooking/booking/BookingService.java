package com.example.ticketBooking.booking;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class BookingService {

  private static final Duration HOLD_DURATION = Duration.ofMinutes(5);

  private final SeatRepository seatRepository;
  private final BookingRepository bookingRepository;
  private final SeatBookingAttemptService seatBookingAttemptService;

  public BookingService(SeatRepository seatRepository, BookingRepository bookingRepository,
      SeatBookingAttemptService seatBookingAttemptService) {
    this.seatRepository = seatRepository;
    this.bookingRepository = bookingRepository;
    this.seatBookingAttemptService = seatBookingAttemptService;
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
      // lost the idempotency-key race — someone else's REQUIRES_NEW transaction
      // committed the insert first. The seat's HELD status from *our* attempt
      // is still committed too (see note below) — that's the part to fix next.
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
      // lost the idempotency-key race — someone else inserted first.
      // Note: our pessimistic seat lock is still held at this point, so no seat
      // corruption occurred — just return the winner's booking.
      return bookingRepository.findByIdempotencyKey(key);
    }
  }
}
