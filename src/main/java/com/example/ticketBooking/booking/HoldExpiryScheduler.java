package com.example.ticketBooking.booking;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


import jakarta.transaction.Transactional;

@Component
public class HoldExpiryScheduler {
  
  private static final Logger log = LoggerFactory.getLogger(HoldExpiryScheduler.class);

  private final BookingRepository bookingRepository;
  private final SeatRepository seatRepository;

  public HoldExpiryScheduler(BookingRepository bookingRepository, SeatRepository seatRepository) {
    this.bookingRepository = bookingRepository;
    this.seatRepository = seatRepository;
  }

  @Scheduled(fixedRate = 60_000)
  @Transactional
  public void releaseExpiredHolds() {
    List<Booking> expired = bookingRepository.findByStatusAndHeldUntilBefore("HELD", Instant.now());
    if (expired.isEmpty()) return;
    
    // prevents N + 1
    List<Long> seatIds = expired.stream().map(Booking::getSeatId).toList();
    List<Long> bookingIds = expired.stream().map(Booking::getId).toList();

    seatRepository.releaseSeats(seatIds);
    bookingRepository.expireBookings(bookingIds);

    if (!expired.isEmpty()) {
      log.info("Released {} expired seat hold(s)", expired.size());
    }
  }
}
