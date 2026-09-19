package com.portfolio.oms.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Implementations must durably deduplicate charge/refund by paymentId and fence charges after void.
 */
public interface PaymentProvider {
  enum Scenario {
    SUCCESS,
    FAILED,
    TIMEOUT
  }

  enum Result {
    SUCCESS,
    FAILED,
    UNKNOWN
  }

  Result charge(UUID paymentId, BigDecimal amount, String currency, Scenario scenario);

  /** Returns true if money was refunded; false if an uncharged payment was voided. */
  boolean voidOrRefund(UUID paymentId);
}
