package com.portfolio.oms.discount;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CouponTest {
  final Instant now = Instant.parse("2026-01-01T00:00:00Z");
  final UUID customer = UUID.randomUUID();

  Coupon coupon() {
    Coupon c = new Coupon();
    c.type = Coupon.Type.PERCENTAGE;
    c.value = new BigDecimal("10");
    c.minimumAmount = new BigDecimal("1000");
    c.maximumDiscount = new BigDecimal("500");
    c.expiresAt = now.plusSeconds(60);
    c.usageLimit = 2;
    c.perCustomerLimit = 1;
    return c;
  }

  @Test
  void capsDiscount() {
    assertThat(coupon().discount(new BigDecimal("10000"), customer, 0, now))
        .isEqualByComparingTo("500");
  }

  @Test
  void expiredAtBoundary() {
    assertThatThrownBy(
            () -> coupon().discount(new BigDecimal("1000"), customer, 0, now.plusSeconds(60)))
        .hasMessageContaining("expired");
  }

  @Test
  void minimum() {
    assertThatThrownBy(() -> coupon().discount(new BigDecimal("999"), customer, 0, now))
        .hasMessageContaining("Minimum");
  }

  @Test
  void eligibility() {
    Coupon c = coupon();
    c.eligibleCustomerId = UUID.randomUUID();
    assertThatThrownBy(() -> c.discount(new BigDecimal("1000"), customer, 0, now))
        .hasMessageContaining("eligible");
  }

  @Test
  void usageLimits() {
    Coupon c = coupon();
    assertThatThrownBy(() -> c.discount(new BigDecimal("1000"), customer, 1, now))
        .hasMessageContaining("limit");
    c.usedCount = 2;
    assertThatThrownBy(() -> c.discount(new BigDecimal("1000"), customer, 0, now))
        .hasMessageContaining("limit");
  }

  @Test
  void fixedCannotExceedSubtotal() {
    Coupon c = coupon();
    c.type = Coupon.Type.FIXED;
    c.value = new BigDecimal("5000");
    c.maximumDiscount = null;
    assertThat(c.discount(new BigDecimal("1000"), customer, 0, now)).isEqualByComparingTo("1000");
  }
}
