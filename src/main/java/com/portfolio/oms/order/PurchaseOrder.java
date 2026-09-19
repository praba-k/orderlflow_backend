package com.portfolio.oms.order;

import com.portfolio.oms.common.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class PurchaseOrder extends BaseEntity {
  public UUID customerId;

  @Enumerated(EnumType.STRING)
  public OrderStatus status = OrderStatus.PENDING;

  @Column(precision = 19, scale = 2)
  public BigDecimal subtotal;

  @Column(precision = 19, scale = 2)
  public BigDecimal discount;

  @Column(precision = 19, scale = 2)
  public BigDecimal tax;

  @Column(precision = 19, scale = 2)
  public BigDecimal total;

  @Column(precision = 7, scale = 6)
  public BigDecimal taxRate;

  public String currency = "INR";

  @Column(length = 1200)
  public String shippingAddress;

  public String couponCode;
  public Instant reservationExpiresAt;

  public void transition(OrderStatus next) {
    status.requireTransition(next);
    status = next;
  }
}
