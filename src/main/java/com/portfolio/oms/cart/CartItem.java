package com.portfolio.oms.cart;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "cart_items")
public class CartItem {
  @Id public UUID id = UUID.randomUUID();
  public UUID cartId;
  public UUID productId;
  public int quantity;
}
