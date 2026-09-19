package com.portfolio.oms.inventory;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StockTest {
  @Test
  void reserveReleaseConsume() {
    Stock s = new Stock();
    s.available = 100;
    s.reserve(3);
    assertThat(s.available).isEqualTo(97);
    assertThat(s.reserved).isEqualTo(3);
    s.release(1);
    s.consume(2);
    assertThat(s.available).isEqualTo(98);
    assertThat(s.reserved).isZero();
  }

  @Test
  void cannotOversell() {
    Stock s = new Stock();
    s.available = 1;
    s.reserve(1);
    assertThatThrownBy(() -> s.reserve(1)).hasMessageContaining("exceeds");
    assertThat(s.available).isZero();
  }

  @Test
  void cannotGoNegative() {
    Stock s = new Stock();
    assertThatThrownBy(() -> s.adjust(-1))
        .isInstanceOf(com.portfolio.oms.common.BusinessException.class);
  }

  @Test
  void rejectsInvalidQuantities() {
    Stock s = new Stock();
    assertThatThrownBy(() -> s.reserve(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> s.release(-1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> s.consume(1))
        .isInstanceOf(com.portfolio.oms.common.BusinessException.class);
  }
}
