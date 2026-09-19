package com.portfolio.oms.cart;

import com.portfolio.oms.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "carts")
public class Cart extends BaseEntity {
  @Column(nullable = false, unique = true)
  public UUID customerId;
}
