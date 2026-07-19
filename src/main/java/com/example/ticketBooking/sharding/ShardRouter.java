package com.example.ticketBooking.sharding;

import java.util.function.Supplier;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Routes by showtimeId, not seatId or bookingId. Almost every hot-path query
 * in this app (seat lookup, booking, waitlist promotion) is already scoped
 * to one showtime, so co-locating a showtime's seats/bookings/waitlist rows
 * on one shard means the hot path never needs a cross-shard query.
 */
@Component
@EnableConfigurationProperties(ShardProperties.class)
public class ShardRouter {

  private final ConsistentHashRing<String> ring = new ConsistentHashRing<>();

  public ShardRouter(ShardProperties properties) {
    properties.getShards().forEach(shard -> ring.addNode(shard.getName()));
  }

  public String resolveShard(Long showtimeId) {
    return ring.getNode("showtime-" + showtimeId);
  }

  /** All configured shard names, e.g. for the scheduler to fan out reads across every shard. */
  public java.util.Set<String> allShards() {
    return ring.nodes();
  }

  /** Runs {@code action} with ShardContext pointed at the shard owning showtimeId, then clears it. */
  public <T> T runOnShard(Long showtimeId, Supplier<T> action) {
    String shard = resolveShard(showtimeId);
    ShardContext.set(shard);
    try {
      return action.get();
    } finally {
      ShardContext.clear();
    }
  }

  /** Runs {@code action} with ShardContext pointed at a specific named shard (used for fan-out reads). */
  public <T> T runOnNamedShard(String shard, Supplier<T> action) {
    ShardContext.set(shard);
    try {
      return action.get();
    } finally {
      ShardContext.clear();
    }
  }
}
