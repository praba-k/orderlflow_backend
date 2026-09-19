package com.portfolio.oms.order;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OrderStatusTest {
  @Test
  void lifecycle() {
    OrderStatus.PENDING.requireTransition(OrderStatus.CONFIRMED);
    OrderStatus.CONFIRMED.requireTransition(OrderStatus.PROCESSING);
    OrderStatus.PROCESSING.requireTransition(OrderStatus.SHIPPED);
    OrderStatus.SHIPPED.requireTransition(OrderStatus.DELIVERED);
  }

  @Test
  void onlyEarlyCancellation() {
    OrderStatus.PENDING.requireTransition(OrderStatus.CANCELLED);
    OrderStatus.CONFIRMED.requireTransition(OrderStatus.CANCELLED);
    assertThatThrownBy(() -> OrderStatus.PROCESSING.requireTransition(OrderStatus.CANCELLED))
        .hasMessageContaining("Cannot transition");
  }

  @Test
  void terminalStatesCannotMove() {
    for (OrderStatus target : OrderStatus.values()) {
      assertThatThrownBy(() -> OrderStatus.DELIVERED.requireTransition(target))
          .isInstanceOf(RuntimeException.class);
      assertThatThrownBy(() -> OrderStatus.CANCELLED.requireTransition(target))
          .isInstanceOf(RuntimeException.class);
    }
  }
}
