package com.example.ticketBooking.booking;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class BookingService {

  private final SeatRepository seatRepository;
  private final BookingRepository bookingRepository;
  private final SeatBookingAttemptService seatBookingAttemptService;

  public BookingService(SeatRepository seatRepository, BookingRepository bookingRepository, SeatBookingAttemptService seatBookingAttemptService) {
    this.seatRepository = seatRepository;
    this.bookingRepository = bookingRepository;
    this.seatBookingAttemptService = seatBookingAttemptService;
  }

  @Transactional
  public Optional<Booking> bookSeat(Long seatId, Long userId, String idempotencyKey) {
    String key = (idempotencyKey != null) ? idempotencyKey : UUID.randomUUID().toString();

    var existing = bookingRepository.findByIdempotencyKey(key);
    if (existing.isPresent()) {
      return existing;
    }

    Optional<Seat> seatOpt = seatRepository.findById(seatId);
    if (seatOpt.isEmpty() || !"AVAILABLE".equals(seatOpt.get().getStatus())) {
      return Optional.empty();
    }

    Seat seat = seatOpt.get();
    seat.setStatus("BOOKED");
    seatRepository.save(seat);

    Booking booking = new Booking(seatId, userId, "CONFIRMED", key);
    return Optional.of(bookingRepository.save(booking));
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
      return Optional.of(seatBookingAttemptService.attemptOptimisticBooking(seatId, userId, key));
    } catch (SeatUnavailableException | org.springframework.orm.ObjectOptimisticLockingFailureException e) {
      return Optional.empty();
    }

  }
}
