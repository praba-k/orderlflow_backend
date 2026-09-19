package com.portfolio.oms.payment;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class MockPaymentProvider implements PaymentProvider {
  private final JdbcTemplate jdbc;

  public MockPaymentProvider(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Result charge(UUID id, BigDecimal amount, String currency, Scenario scenario) {
    jdbc.update(
        "insert into mock_provider_ledger(payment_id,state,amount,currency) values (?,'NEW',?,?) on"
            + " conflict do nothing",
        id,
        amount,
        currency);
    Map<String, Object> row =
        jdbc.queryForMap("select * from mock_provider_ledger where payment_id=? for update", id);
    String state = (String) row.get("state");
    if (state.equals("FENCED") || state.equals("REFUNDED")) return Result.FAILED;
    if (((BigDecimal) row.get("amount")).compareTo(amount) != 0
        || !currency.equals(row.get("currency")))
      throw new IllegalArgumentException("Provider idempotency mismatch");
    if (state.equals("SUCCESS")) return Result.SUCCESS;
    if (state.equals("FAILED")) return Result.FAILED;
    Result result =
        switch (scenario) {
          case SUCCESS -> Result.SUCCESS;
          case FAILED -> Result.FAILED;
          case TIMEOUT -> Result.UNKNOWN;
        };
    if (result != Result.UNKNOWN)
      jdbc.update(
          "update mock_provider_ledger set state=?,charge_count=? where payment_id=?",
          result.name(),
          result == Result.SUCCESS ? 1 : 0,
          id);
    return result;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean voidOrRefund(UUID id) {
    jdbc.update(
        "insert into mock_provider_ledger(payment_id,state,amount,currency) values"
            + " (?,'FENCED',0,'INR') on conflict do nothing",
        id);
    String state =
        jdbc.queryForObject(
            "select state from mock_provider_ledger where payment_id=? for update",
            String.class,
            id);
    boolean refunded = Set.of("SUCCESS", "REFUNDED").contains(state);
    jdbc.update(
        "update mock_provider_ledger set state=? where payment_id=?",
        refunded ? "REFUNDED" : "FENCED",
        id);
    return refunded;
  }
}
