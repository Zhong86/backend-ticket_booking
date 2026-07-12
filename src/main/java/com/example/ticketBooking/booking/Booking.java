package com.example.ticketBooking.booking;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "seat_id", nullable = false)
  private Long seatId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false)
  private String status; // PENDING, HELD, CONFIRMED, CANCELLED, EXPIRED

  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;

  @Column(name = "held_until")
  private Instant heldUntil; 

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public void markHeld(Instant heldUntil) {
    this.status = "HELD"; 
    this.heldUntil = heldUntil;
  }

  public void markConfirmed() {
    this.status = "CONFIRMED"; 
    this.heldUntil = null;
  }

  public void markExpired() {
    this.status = "EXPIRED"; 
  }

  public Booking(Long seatId, Long userId, String idempotencyKey) {
    this.seatId = seatId;
    this.userId = userId;
    this.idempotencyKey = idempotencyKey;
  }

}
