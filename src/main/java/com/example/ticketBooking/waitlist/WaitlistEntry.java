package com.example.ticketBooking.waitlist;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "waitlist_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "showtime_id", nullable = false)
  private Long showtimeId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "joined_at", nullable = false, updatable = false)
  private Instant joinedAt = Instant.now();

  @Column(name = "notified_at")
  private Instant notifiedAt;

  public WaitlistEntry(Long showtimeId, Long userId) {
    this.showtimeId = showtimeId;
    this.userId = userId;
  }

}
