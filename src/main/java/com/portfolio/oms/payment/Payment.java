package com.portfolio.oms.payment;

import com.portfolio.oms.common.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {
  @Column(unique = true)
  public UUID orderId;

  @Column(precision = 19, scale = 2)
  public BigDecimal amount;

  public String currency;

  @Enumerated(EnumType.STRING)
  public Status status = Status.INITIATED;

  @Enumerated(EnumType.STRING)
  public PaymentProvider.Scenario scenario;

  public int attempts;
  public Instant nextAttemptAt = Instant.now();
  public String lastError;

  public enum Status {
    INITIATED,
    SUCCESS,
    FAILED,
    REFUND_PENDING,
    REFUNDED
  }
}
