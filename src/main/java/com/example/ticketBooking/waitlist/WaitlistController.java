package com.example.ticketBooking.waitlist;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ticketBooking.sharding.ShardRouter;

@RestController
@RequestMapping("/showtimes/{showtimeId}/waitlist")
public class WaitlistController {

  private final WaitlistService waitlistService;
  private final ShardRouter shardRouter;

  public WaitlistController(WaitlistService waitlistService, ShardRouter shardRouter) {
    this.waitlistService = waitlistService;
    this.shardRouter = shardRouter;
  }

  public record JoinRequest(Long userId) {
  }

  @PostMapping
  public ResponseEntity<?> join(@PathVariable Long showtimeId, @RequestBody JoinRequest request) {
    try {
      WaitlistEntry entry = shardRouter.runOnShard(showtimeId,
          () -> waitlistService.joinWaitlist(showtimeId, request.userId()));
      return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
  }
}
