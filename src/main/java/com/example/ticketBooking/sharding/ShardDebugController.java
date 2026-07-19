package com.example.ticketBooking.sharding;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Not for production — exists purely so you can ask "which shard owns
 * showtime 7?" from curl instead of re-deriving the MD5 hash by hand.
 */
@RestController
@RequestMapping("/debug/shards")
public class ShardDebugController {

  private final ShardRouter shardRouter;

  public ShardDebugController(ShardRouter shardRouter) {
    this.shardRouter = shardRouter;
  }

  @GetMapping
  public java.util.Set<String> allShards() {
    return shardRouter.allShards();
  }

  @GetMapping("/{showtimeId}")
  public java.util.Map<String, Object> resolve(@PathVariable Long showtimeId) {
    return java.util.Map.of(
        "showtimeId", showtimeId,
        "shard", shardRouter.resolveShard(showtimeId));
  }
}
