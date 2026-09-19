package com.portfolio.oms.payment;

import com.portfolio.oms.authentication.CurrentCustomer;
import com.portfolio.oms.common.BusinessException;
import com.portfolio.oms.notification.NotificationService;
import com.portfolio.oms.order.*;
import java.time.Clock;
import java.util.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentService {
  private final PaymentRepository payments;
  private final OrderService orders;
  private final CurrentCustomer current;
  private final Clock clock;
  private final NotificationService notifications;

  public PaymentService(
      PaymentRepository p, OrderService o, CurrentCustomer c, Clock t, NotificationService n) {
    payments = p;
    orders = o;
    current = c;
    clock = t;
    notifications = n;
  }

  public Payment initiate(PurchaseOrder order, PaymentProvider.Scenario scenario) {
    Payment p = new Payment();
    p.orderId = order.id;
    p.amount = order.total;
    p.currency = order.currency;
    p.scenario = scenario;
    return payments.save(p);
  }

  @Transactional(readOnly = true)
  public Payment snapshot(UUID id) {
    return payments.findById(id).orElseThrow(() -> BusinessException.missing("PAYMENT"));
  }

  public Payment forOrder(UUID id) {
    orders.get(id);
    return payments.findByOrderId(id).orElseThrow(() -> BusinessException.missing("PAYMENT"));
  }

  public Payment retry(UUID id) {
    PurchaseOrder o = orders.lock(id);
    current.ownerOrAdmin(o.customerId);
    Payment p = payments.findByOrderId(id).orElseThrow(() -> BusinessException.missing("PAYMENT"));
    if (p.status != Payment.Status.INITIATED && p.status != Payment.Status.REFUND_PENDING) return p;
    p.nextAttemptAt = clock.instant();
    return p;
  }

  @EventListener
  public void cancelled(OrderChanged event) {
    if (event.status() != OrderStatus.CANCELLED) return;
    payments
        .findByOrderId(event.orderId())
        .ifPresent(
            p -> {
              if (p.status == Payment.Status.SUCCESS || p.status == Payment.Status.INITIATED) {
                p.status = Payment.Status.REFUND_PENDING;
                p.nextAttemptAt = clock.instant();
              }
            });
  }

  public void settle(UUID id, PaymentProvider.Result result) {
    PurchaseOrder o = orders.lock(payments.findOrderId(id).orElseThrow());
    Payment p = payments.lockById(id).orElseThrow();
    if (p.status != Payment.Status.INITIATED) return;
    p.attempts++;
    switch (result) {
      case SUCCESS -> {
        if (orders.paymentSucceeded(o)) {
          p.status = Payment.Status.SUCCESS;
          notifications.record(o.customerId, o.id, "PAYMENT_SUCCESSFUL");
        } else {
          p.status = Payment.Status.REFUND_PENDING;
          p.nextAttemptAt = clock.instant();
        }
      }
      case FAILED -> {
        p.status = Payment.Status.FAILED;
        p.lastError = "PROVIDER_DECLINED";
        orders.paymentFailed(o);
        notifications.record(o.customerId, o.id, "PAYMENT_FAILED");
      }
      case UNKNOWN -> {
        p.lastError = "PROVIDER_TIMEOUT";
        p.nextAttemptAt = clock.instant().plusSeconds(Math.min(60, 1L << Math.min(p.attempts, 6)));
      }
    }
  }

  public void refundSettled(UUID id, boolean refunded) {
    PurchaseOrder o = orders.lock(payments.findOrderId(id).orElseThrow());
    Payment p = payments.lockById(id).orElseThrow();
    if (p.status != Payment.Status.REFUND_PENDING) return;
    p.status = refunded ? Payment.Status.REFUNDED : Payment.Status.FAILED;
    p.lastError = refunded ? null : "VOIDED";
    notifications.record(o.customerId, o.id, refunded ? "PAYMENT_REFUNDED" : "PAYMENT_FAILED");
  }

  public void providerUnavailable(UUID id) {
    orders.lock(payments.findOrderId(id).orElseThrow());
    Payment p = payments.lockById(id).orElseThrow();
    if (p.status == Payment.Status.INITIATED || p.status == Payment.Status.REFUND_PENDING) {
      p.attempts++;
      p.lastError = "PROVIDER_UNAVAILABLE";
      p.nextAttemptAt = clock.instant().plusSeconds(60);
    }
  }
}
