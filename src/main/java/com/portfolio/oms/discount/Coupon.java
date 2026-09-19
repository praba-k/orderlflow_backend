package com.portfolio.oms.discount;

import com.portfolio.oms.common.BaseEntity;
import com.portfolio.oms.common.BusinessException;
import jakarta.persistence.*;
import java.math.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coupons")
public class Coupon extends BaseEntity {
  @Column(nullable = false, unique = true)
  public String code;

  @Enumerated(EnumType.STRING)
  public Type type;

  @Column(precision = 19, scale = 2)
  public BigDecimal value;

  @Column(precision = 19, scale = 2)
  public BigDecimal minimumAmount;

  @Column(precision = 19, scale = 2)
  public BigDecimal maximumDiscount;

  public Instant expiresAt;
  public int usageLimit;
  public int usedCount;
  public int perCustomerLimit;
  public UUID eligibleCustomerId;
  public boolean active = true;

  public enum Type {
    PERCENTAGE,
    FIXED
  }

  public BigDecimal discount(BigDecimal subtotal, UUID customer, int customerUses, Instant now) {
    if (!active || !now.isBefore(expiresAt))
      throw BusinessException.invalid("INVALID_COUPON", "Coupon is inactive or expired");

    if (usedCount >= usageLimit || customerUses >= perCustomerLimit)
      throw BusinessException.invalid("INVALID_COUPON", "Coupon usage limit reached");

    if (eligibleCustomerId != null && !eligibleCustomerId.equals(customer))
      throw BusinessException.invalid("INVALID_COUPON", "Customer is not eligible");

    if (subtotal.compareTo(minimumAmount) < 0)
      throw BusinessException.invalid("INVALID_COUPON", "Minimum order amount not reached");

    BigDecimal d = type == Type.PERCENTAGE
            ? subtotal.multiply(value).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
            : value;

    if (maximumDiscount != null) d = d.min(maximumDiscount);
    return d.min(subtotal).setScale(2, RoundingMode.HALF_UP);
  }
}
