package com.example.ticketBooking.sharding;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Spring's routing DataSource looks up {@link #determineCurrentLookupKey()}
 * every time a connection is requested, and hands back whichever target
 * DataSource is registered under that key. We just point it at ShardContext.
 */
public class ShardRoutingDataSource extends AbstractRoutingDataSource {

  @Override
  protected Object determineCurrentLookupKey() {
    return ShardContext.get();
  }
}
