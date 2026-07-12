package com.example.ticketBooking.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import io.lettuce.core.dynamic.annotation.Param;

public interface BookingRepository extends JpaRepository<Booking, Long>{
  Optional<Booking> findByIdempotencyKey(String idempotencyKey);

  List<Booking> findByStatusAndHeldUntilBefore(String status, Instant cutoff);

  @Modifying
  @Query("UPDATE Booking b SET b.status = 'EXPIRED' WHERE b.id in :bookingIds")
  void expireBookings(@Param("bookingIds") List<Long> bookingIds);
}
