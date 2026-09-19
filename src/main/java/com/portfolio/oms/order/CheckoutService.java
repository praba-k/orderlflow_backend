package com.portfolio.oms.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.oms.common.BusinessException;
import com.portfolio.oms.customer.CustomerRepository;
import com.portfolio.oms.payment.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutService {
  private final CustomerRepository customers;
  private final IdempotencyRepository keys;
  private final OrderService orders;
  private final PaymentService payments;
  private final ObjectMapper json;

  public CheckoutService(
      CustomerRepository c,
      IdempotencyRepository k,
      OrderService o,
      PaymentService p,
      ObjectMapper j) {
    customers = c;
    keys = k;
    orders = o;
    payments = p;
    json = j;
  }

  @Transactional
  public Result checkout(UUID customer, String key, OrderController.Checkout r) {
    if (key == null || !key.matches("[A-Za-z0-9._:-]{1,100}"))
      throw new IllegalArgumentException("Idempotency-Key is required (1-100 safe characters)");
    var c = customers.lockById(customer).orElseThrow(() -> BusinessException.missing("CUSTOMER"));
    if (!c.active) throw BusinessException.conflict("CUSTOMER_INACTIVE", "Customer is inactive");
    String coupon = r.couponCode() == null ? "" : r.couponCode().trim().toUpperCase(Locale.ROOT);
    String fingerprint = fingerprint(r.addressId() + "|" + coupon + "|" + r.paymentScenario());
    var existing = keys.findByCustomerIdAndRequestKey(customer, key);
    if (existing.isPresent()) {
      var previous = existing.get();
      if (!previous.requestFingerprint.equals(fingerprint))
        throw BusinessException.conflict(
            "IDEMPOTENCY_KEY_REUSED", "Key was already used for a different request");
      try {
        return new Result(json.readValue(previous.responseBody, OrderService.View.class), true);
      } catch (Exception e) {
        throw new IllegalStateException("Stored response cannot be decoded", e);
      }
    }
    OrderService.View view = orders.create(customer, r.addressId(), coupon);
    payments.initiate(view.order(), r.paymentScenario());
    IdempotencyRecord record = new IdempotencyRecord();
    record.customerId = customer;
    record.requestKey = key;
    record.requestFingerprint = fingerprint;
    record.orderId = view.order().id;
    try {
      record.responseBody = json.writeValueAsString(view);
    } catch (Exception e) {
      throw new IllegalStateException("Response cannot be stored", e);
    }
    keys.save(record);
    return new Result(view, false);
  }

  static String fingerprint(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public record Result(OrderService.View view, boolean replay) {}
}
