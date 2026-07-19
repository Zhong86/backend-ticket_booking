package com.example.ticketBooking.sharding;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sharding")
public class ShardProperties {

  private List<Shard> shards;

  public List<Shard> getShards() {
    return shards;
  }

  public void setShards(List<Shard> shards) {
    this.shards = shards;
  }

  public static class Shard {
    private String name;
    private String url;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }
  }
}
