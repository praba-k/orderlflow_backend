package com.portfolio.oms.order;

import java.math.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PricingService {
  private final BigDecimal taxRate;

  public PricingService(@Value("${app.tax-rate}") BigDecimal rate) {
    if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0)
      throw new IllegalArgumentException("Tax rate must be between 0 and 1");
    taxRate = rate.setScale(6, RoundingMode.UNNECESSARY);
  }

  public Totals calculate(BigDecimal subtotal, BigDecimal discount) {
    if (subtotal.signum() < 0 || discount.signum() < 0 || discount.compareTo(subtotal) > 0)
      throw new IllegalArgumentException("Invalid pricing amounts");
    BigDecimal net = subtotal.subtract(discount);
    BigDecimal tax = net.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
    return new Totals(
        subtotal.setScale(2, RoundingMode.HALF_UP),
        discount.setScale(2, RoundingMode.HALF_UP),
        tax,
        net.add(tax).setScale(2, RoundingMode.HALF_UP),
        taxRate);
  }

  public record Totals(
      BigDecimal subtotal,
      BigDecimal discount,
      BigDecimal tax,
      BigDecimal total,
      BigDecimal taxRate) {}
}
