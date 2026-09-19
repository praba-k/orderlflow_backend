package com.portfolio.oms.order;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PricingServiceTest {
  @Test
  void discountPrecedesTax() {
    var t =
        new PricingService(new BigDecimal("0.18"))
            .calculate(new BigDecimal("1000"), new BigDecimal("100"));
    assertThat(t.total()).isEqualByComparingTo("1062.00");
    assertThat(t.tax()).isEqualByComparingTo("162.00");
  }

  @Test
  void roundsHalfUp() {
    var t =
        new PricingService(new BigDecimal("0.1"))
            .calculate(new BigDecimal("0.05"), BigDecimal.ZERO);
    assertThat(t.tax()).isEqualByComparingTo("0.01");
  }

  @Test
  void rejectsNegativeNet() {
    assertThatThrownBy(
            () -> new PricingService(BigDecimal.ZERO).calculate(BigDecimal.ZERO, BigDecimal.ONE))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
