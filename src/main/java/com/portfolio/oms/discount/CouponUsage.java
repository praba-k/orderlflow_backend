package com.portfolio.oms.discount;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coupon_usage")
public class CouponUsage {
  @Id public UUID id = UUID.randomUUID();
  public UUID couponId;
  public UUID customerId;
  public UUID orderId;
  public Instant createdAt = Instant.now();
}
