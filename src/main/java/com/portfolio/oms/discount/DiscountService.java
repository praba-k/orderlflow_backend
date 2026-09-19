package com.portfolio.oms.discount;

import com.portfolio.oms.common.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DiscountService {
  private final CouponRepository coupons;
  private final CouponUsageRepository usages;
  private final Clock clock;

  public DiscountService(CouponRepository c, CouponUsageRepository u, Clock t) {
    coupons = c;
    usages = u;
    clock = t;
  }

  public BigDecimal redeem(String code, BigDecimal subtotal, UUID customer, UUID order) {
    if (code == null || code.isBlank()) return new BigDecimal("0.00");
    Coupon c =
        coupons
            .lockByCode(code.trim().toUpperCase(Locale.ROOT))
            .orElseThrow(
                () -> BusinessException.invalid("INVALID_COUPON", "Coupon does not exist"));
    BigDecimal d =
        c.discount(
            subtotal,
            customer,
            usages.countByCouponIdAndCustomerId(c.id, customer),
            clock.instant());
    c.usedCount++;
    CouponUsage u = new CouponUsage();
    u.couponId = c.id;
    u.customerId = customer;
    u.orderId = order;
    usages.save(u);
    return d;
  }

  public Coupon create(DiscountController.Input r) {
    return save(null, r);
  }

  public Coupon save(UUID id, DiscountController.Input r) {
    if (r.type() == Coupon.Type.PERCENTAGE && r.value().compareTo(new BigDecimal("100")) > 0)
      throw new IllegalArgumentException("Percentage exceeds 100");
    Coupon c =
        id == null
            ? new Coupon()
            : coupons.lockById(id).orElseThrow(() -> BusinessException.missing("COUPON"));
    if (r.usageLimit() < c.usedCount)
      throw BusinessException.conflict(
          "COUPON_LIMIT", "Usage limit cannot be below existing redemptions");
    c.code = r.code().toUpperCase(Locale.ROOT);
    c.type = r.type();
    c.value = r.value();
    c.minimumAmount = r.minimumAmount();
    c.maximumDiscount = r.maximumDiscount();
    c.expiresAt = r.expiresAt();
    c.usageLimit = r.usageLimit();
    c.perCustomerLimit = r.perCustomerLimit();
    c.eligibleCustomerId = r.eligibleCustomerId();
    return coupons.save(c);
  }

  public void deactivate(UUID id) {
    Coupon c = coupons.findById(id).orElseThrow(() -> BusinessException.missing("COUPON"));
    c.active = false;
  }

  public org.springframework.data.domain.Page<Coupon> list(
      org.springframework.data.domain.Pageable p) {
    return coupons.findAll(p);
  }

  public Coupon active(UUID id, boolean active) {
    Coupon c = coupons.lockById(id).orElseThrow(() -> BusinessException.missing("COUPON"));
    c.active = active;
    return c;
  }

  @Transactional(readOnly = true)
  public BigDecimal preview(String code, BigDecimal subtotal, UUID customer) {
    if (code == null || code.isBlank()) return new BigDecimal("0.00");
    Coupon c =
        coupons
            .findByCode(code.trim().toUpperCase(Locale.ROOT))
            .orElseThrow(
                () -> BusinessException.invalid("INVALID_COUPON", "Coupon does not exist"));
    return c.discount(
        subtotal, customer, usages.countByCouponIdAndCustomerId(c.id, customer), clock.instant());
  }
}
