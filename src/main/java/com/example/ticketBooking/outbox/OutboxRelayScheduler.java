package com.example.ticketBooking.outbox;


import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.transaction.Transactional;

@Component
public class OutboxRelayScheduler {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);
  private final OutboxEventRepository outboxEventRepository; 
  private final RabbitTemplate rabbitTemplate; 

  @Value("${notification.rabbitmq.exchange}")
  private String exchange; 

  @Value("${notification.rabbitmq.routing-key}")
  private String routingKey;

  public OutboxRelayScheduler(OutboxEventRepository outboxEventRepository, RabbitTemplate rabbitTemplate) {
    this.outboxEventRepository = outboxEventRepository;
    this.rabbitTemplate = rabbitTemplate;
  }

  @Scheduled(fixedRate = 5_000)
  @Transactional
  public void relayPendingEvents() {
    List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderById("PENDING");
    if(pending.isEmpty()) return; 

    List<Long> publishedIds = new ArrayList<>();

    for (OutboxEvent event : pending) {
      try {
        log.info("Event payload: {}", event.getPayload());
        rabbitTemplate.convertAndSend(exchange, routingKey, event.getPayload());
        publishedIds.add(event.getId());
      } catch (Exception e) {
        log.error("Failed to publish outbox event {}: {}", event.getId(), e.getMessage());
      }
    }

    if (!publishedIds.isEmpty()) {
      outboxEventRepository.markPublished(publishedIds);
      log.info("Relayed {} outbox event(s) to notification queue", publishedIds.size());
    }
  }

}
