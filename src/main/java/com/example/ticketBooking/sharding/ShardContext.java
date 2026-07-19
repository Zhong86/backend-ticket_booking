package com.example.ticketBooking.sharding;

/**
 * Holds the shard name for the current thread. Set by {@link ShardRouter}
 * before a shard-scoped operation runs; read by {@link ShardRoutingDataSource}
 * every time JPA/Hibernate asks for a connection.
 *
 * A null value means "no shard resolved" -- ShardRoutingDataSource falls back
 * to its configured default target in that case, which is what serves the
 * global tables (events, venues, showtimes, outbox_events) that intentionally
 * live on one instance rather than being sharded. See ShardDataSourceConfig
 * for why that's a simplification worth knowing about.
 */
public final class ShardContext {

  private static final ThreadLocal<String> CURRENT_SHARD = new ThreadLocal<>();

  private ShardContext() {}

  public static void set(String shard) {
    CURRENT_SHARD.set(shard);
  }

  public static String get() {
    return CURRENT_SHARD.get();
  }

  public static void clear() {
    CURRENT_SHARD.remove();
  }
}
