package com.portfolio.oms.product;

import com.portfolio.oms.common.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product extends BaseEntity {
  @Column(nullable = false)
  public String name;

  @Column(length = 4000)
  public String description;

  @Column(nullable = false)
  public UUID categoryId;

  @Column(nullable = false, precision = 19, scale = 2)
  public BigDecimal price;

  @Column(nullable = false, unique = true)
  public String sku;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public Status status = Status.ACTIVE;

  public enum Status {
    ACTIVE,
    INACTIVE
  }
}
