package com.example.ticketBooking.waitlist;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/showtimes/{showtimeId}/waitlist")
public class WaitlistController {

  private final WaitlistService waitlistService;

  public WaitlistController(WaitlistService waitlistService) {
    this.waitlistService = waitlistService;
  }

  public record JoinRequest(Long userId) {}

  @PostMapping
  public ResponseEntity<?> join(
  @PathVariable Long showtimeId, @RequestBody JoinRequest request
  ) {
    try {
      WaitlistEntry entry = waitlistService.joinWaitlist(showtimeId, request.userId());
      return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
  }
}
