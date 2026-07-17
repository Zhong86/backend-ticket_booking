package com.example.ticketBooking.waitlist;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long>{
  Optional<WaitlistEntry> findByShowtimeIdAndUserId(Long showtimeId, Long userId);

  List<WaitlistEntry> findByNotifiedAtIsNullOrderByShowtimeIdAscJoinedAtAsc();
}
