package com.portfolio.oms.notification;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {
  @Id public UUID id = UUID.randomUUID();
  public UUID customerId;
  public UUID orderId;
  public String eventType;
  public Instant createdAt = Instant.now();
  public boolean read;
}
