package com.example.ticketBooking.sharding;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * SIMPLIFICATION WORTH KNOWING FOR THE INTERVIEW:
 *
 * This wires ONE routing DataSource for the whole app. Every shard runs the
 * full schema.sql, so events/venues/showtimes/outbox_events technically exist
 * on all 3 shards, but only seats/bookings/waitlist_entries are meaningfully
 * *sharded* -- i.e. only those tables are ever queried through ShardRouter.
 * Reads/writes to events/venues/outbox happen with no ShardContext set, so
 * they silently land on shard-0 (the default target) and only shard-0's copy
 * is ever the source of truth for them.
 *
 * A production system would split this into two DataSources/EntityManagerFactories:
 * one routing DataSource for shard-owned entities, one fixed DataSource for
 * global/reference entities -- with @EnableJpaRepositories(basePackages=...,
 * entityManagerFactoryRef=...) pointing each repository package at the right
 * factory. That's a meaningful chunk of additional Spring config for a demo
 * project, so it's left as a "here's what I'd do next" talking point rather
 * than implemented.
 */
@Configuration
@EnableConfigurationProperties(ShardProperties.class)
public class ShardDataSourceConfig {

  @Value("${spring.datasource.username}")
  private String username;

  @Value("${spring.datasource.password}")
  private String password;

  @Bean
  @Primary
  public DataSource dataSource(ShardProperties properties) {
    Map<Object, Object> targets = new HashMap<>();
    DataSource defaultTarget = null;

    for (ShardProperties.Shard shard : properties.getShards()) {
      DataSource ds = DataSourceBuilder.create()
          .url(shard.getUrl())
          .username(username)
          .password(password)
          .driverClassName("org.postgresql.Driver")
          .build();
      targets.put(shard.getName(), ds);
      if (defaultTarget == null) {
        defaultTarget = ds; // first configured shard doubles as the home for global tables
      }
    }

    ShardRoutingDataSource routingDataSource = new ShardRoutingDataSource();
    routingDataSource.setTargetDataSources(targets);
    routingDataSource.setDefaultTargetDataSource(defaultTarget);
    routingDataSource.afterPropertiesSet();
    return routingDataSource;
  }
}
