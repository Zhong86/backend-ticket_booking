package com.example.ticketBooking.booking;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface SeatRepository extends JpaRepository<Seat, Long>{
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Override
  Optional<Seat> findById(Long id);

  @Query("SELECT s FROM Seat s WHERE s.id = :id")
  Optional<Seat> findByIdNoLock(@Param("id") Long id);

}
