package com.portfolio.oms.order;

import com.portfolio.oms.common.BusinessException;

public enum OrderStatus {
  PENDING,
  CONFIRMED,
  PROCESSING,
  SHIPPED,
  DELIVERED,
  CANCELLED;

  public void requireTransition(OrderStatus target) {
    boolean valid =
        switch (this) {
          case PENDING -> target == CONFIRMED || target == CANCELLED;
          case CONFIRMED -> target == PROCESSING || target == CANCELLED;
          case PROCESSING -> target == SHIPPED;
          case SHIPPED -> target == DELIVERED;
          default -> false;
        };
    if (!valid)
      throw BusinessException.conflict(
          "INVALID_ORDER_STATE", "Cannot transition " + this + " to " + target);
  }
}
