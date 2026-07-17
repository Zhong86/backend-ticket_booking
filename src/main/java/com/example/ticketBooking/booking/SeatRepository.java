package com.example.ticketBooking.booking;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

public interface SeatRepository extends JpaRepository<Seat, Long> {
  // Pessimistic Locking
  @Transactional
  // @Lock(LockModeType.PESSIMISTIC_WRITE)
  // @Override
  Optional<Seat> findById(Long id);

  // Optimistic Locking
  @Query("SELECT s FROM Seat s WHERE s.id = :id")
  Optional<Seat> findByIdNoLock(@Param("id") Long id);

  @Modifying // tells Spring this is a WRITE; uses Entity name NOT table
  @Query("UPDATE Seat s SET s.status = 'AVAILABLE' WHERE s.id in :seatIds")
  void releaseSeats(@Param("seatIds") List<Long> seatIds);

  long countByShowtimeIdAndStatus(Long showtimeId, String status);
  List<Seat> findByShowtimeIdInAndStatusOrderByShowtimeIdAscIdAsc(List<Long> showtimeIds, String status);
}
