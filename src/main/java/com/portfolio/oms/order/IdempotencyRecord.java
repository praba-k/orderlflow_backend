package com.portfolio.oms.order;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {
  @Id public UUID id = UUID.randomUUID();
  public UUID customerId;
  public String requestKey;
  public String requestFingerprint;
  public UUID orderId;

  @Column(columnDefinition = "text")
  public String responseBody;

  public Instant createdAt = Instant.now();
}
