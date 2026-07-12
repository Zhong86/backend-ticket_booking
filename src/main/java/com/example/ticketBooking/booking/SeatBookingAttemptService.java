package com.example.ticketBooking.booking;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeatBookingAttemptService {

  private final SeatRepository seatRepository;
  private final BookingRepository bookingRepository;

  public SeatBookingAttemptService(SeatRepository seatRepository, BookingRepository bookingRepository) {
    this.seatRepository = seatRepository;
    this.bookingRepository = bookingRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Booking attemptOptimisticBooking(Long seatId, Long userId, String idempotencyKey, Duration hold_dur) {
    Seat seat = seatRepository.findByIdNoLock(seatId)
        .orElseThrow(() -> new SeatUnavailableException(seatId));

    if (!"AVAILABLE".equals(seat.getStatus())) {
      throw new SeatUnavailableException(seatId);
    }

    seat.setStatus("HELD");
    seatRepository.saveAndFlush(seat); // throws here if version changed underneath us

    Booking booking = new Booking(seatId, userId, idempotencyKey);
    booking.markHeld(Instant.now().plus(hold_dur));
    return bookingRepository.saveAndFlush(booking);
  }
}
