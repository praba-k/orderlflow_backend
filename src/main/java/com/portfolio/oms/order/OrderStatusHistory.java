package com.portfolio.oms.order;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {
  @Id public UUID id = UUID.randomUUID();
  public UUID orderId;

  @Enumerated(EnumType.STRING)
  public OrderStatus status;

  public Instant createdAt = Instant.now();
}
