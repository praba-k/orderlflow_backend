package com.portfolio.oms.inventory;

import com.portfolio.oms.common.BusinessException;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "inventory")
public class Stock {
  @Id public UUID productId;
  public int available;
  public int reserved;
  @Version public long version;

  public void reserve(int quantity) {
    positive(quantity);
    if (available < quantity)
      throw BusinessException.conflict(
          "INSUFFICIENT_INVENTORY", "Requested quantity exceeds available inventory");
    available -= quantity;
    reserved = Math.addExact(reserved, quantity);
  }

  public void release(int quantity) {
    positive(quantity);
    if (reserved < quantity)
      throw BusinessException.conflict("INVALID_RESERVATION", "Reservation is insufficient");
    reserved -= quantity;
    available = Math.addExact(available, quantity);
  }

  public void consume(int quantity) {
    positive(quantity);
    if (reserved < quantity)
      throw BusinessException.conflict("INVALID_RESERVATION", "Reservation is insufficient");
    reserved -= quantity;
  }

  public void adjust(int delta) {
    if (delta == 0) throw new IllegalArgumentException("Zero adjustment");
    if ((long) available + delta < 0 || (long) available + delta > Integer.MAX_VALUE)
      throw BusinessException.conflict(
          "INSUFFICIENT_INVENTORY", "Adjustment exceeds inventory bounds");
    available += delta;
  }

  private static void positive(int q) {
    if (q <= 0) throw new IllegalArgumentException("Quantity must be positive");
  }
}
