package com.example.ticketBooking.ratelimit;

import java.time.Duration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RateLimiterService {

  private static final Duration WINDOW = Duration.ofMinutes(1);
  private final RedisTemplate<String, Object> redisTemplate;

  public RateLimiterService(RedisTemplate<String, Object> redisTemplate) {
    this.redisTemplate = redisTemplate;
  } 
  
  public boolean allow(String key, int maxRequests) {
    Long count = redisTemplate.opsForValue().increment(key);
    if (count != null && count == 1L) {
      redisTemplate.expire(key, WINDOW);
    }
    return count != null && count <= maxRequests;
  }
}
