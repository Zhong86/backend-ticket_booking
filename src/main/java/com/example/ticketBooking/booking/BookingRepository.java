package com.example.ticketBooking.booking;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookingRepository extends JpaRepository<Booking, Long>{
  Optional<Booking> findByIdempotencyKey(String idempotencyKey);
}
