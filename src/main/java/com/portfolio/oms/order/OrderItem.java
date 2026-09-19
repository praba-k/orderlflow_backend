package com.portfolio.oms.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItem {
  @Id public UUID id = UUID.randomUUID();
  public UUID orderId;
  public UUID productId;
  public String productName;
  public String sku;
  public int quantity;

  @Column(precision = 19, scale = 2)
  public BigDecimal priceAtPurchase;
}
