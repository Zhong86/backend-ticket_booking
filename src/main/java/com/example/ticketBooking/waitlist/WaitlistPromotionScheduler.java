package com.example.ticketBooking.waitlist;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WaitlistPromotionScheduler {
  
  private static final Logger log = LoggerFactory.getLogger(WaitlistPromotionScheduler.class);
  private final WaitlistService waitlistService;

  public WaitlistPromotionScheduler(WaitlistService waitlistService) {
    this.waitlistService = waitlistService;
  }

  @Scheduled(fixedRate = 15_000) 
  public void promote() {
    log.info("Starting Waitlist Scheduler");
    waitlistService.promoteWaitlist();
  }
}
