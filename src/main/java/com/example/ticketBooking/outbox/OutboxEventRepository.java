package com.example.ticketBooking.outbox;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import io.lettuce.core.dynamic.annotation.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long>{
  List<OutboxEvent> findByStatusOrderById(String status);

  @Modifying
  @Query("UPDATE OutboxEvent o SET o.status = 'PUBLISHED', o.publishedAt = CURRENT_TIMESTAMP WHERE o.id IN :ids")
  void markPublished(@Param("ids") List<Long> ids);
}
