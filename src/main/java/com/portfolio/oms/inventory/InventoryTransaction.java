package com.portfolio.oms.inventory;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_transactions")
public class InventoryTransaction {
  @Id public UUID id = UUID.randomUUID();
  public UUID productId;
  public UUID orderId;
  public UUID actorId;
  public String operation;
  public int quantity;
  public int availableAfter;
  public int reservedAfter;
  public String reason;
  public Instant createdAt = Instant.now();
}
