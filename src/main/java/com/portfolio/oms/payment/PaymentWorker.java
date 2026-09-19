package com.portfolio.oms.payment;

import com.portfolio.oms.order.*;
import java.time.Clock;
import java.util.*;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentWorker {
  private final PaymentRepository payments;
  private final PaymentService service;
  private final PaymentProvider provider;
  private final OrderRepository orders;
  private final OrderService orderService;
  private final Clock clock;
  private static final Logger log = LoggerFactory.getLogger(PaymentWorker.class);

  public PaymentWorker(
      PaymentRepository p,
      PaymentService s,
      PaymentProvider pr,
      OrderRepository o,
      OrderService os,
      Clock t) {
    payments = p;
    service = s;
    provider = pr;
    orders = o;
    orderService = os;
    clock = t;
  }

  @Scheduled(fixedDelayString = "${app.payment.poll-ms}")
  public void poll() {
    for (var o :
        orders.findTop100ByStatusAndReservationExpiresAtBefore(
            OrderStatus.PENDING, clock.instant())) {
      try {
        orderService.expire(o.id);
      } catch (Exception e) {
        log.warn(
            "Reservation expiry failed orderId={} type={}", o.id, e.getClass().getSimpleName());
      }
    }
    for (var p :
        payments.findTop100ByStatusInAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(
            List.of(Payment.Status.INITIATED, Payment.Status.REFUND_PENDING), clock.instant()))
      process(p.id);
  }

  public void process(UUID id) {
    Payment p = service.snapshot(id);
    try (var paymentScope = MDC.putCloseable("paymentId", id.toString());
        var orderScope = MDC.putCloseable("orderId", p.orderId.toString())) {
      try {
        if (p.status == Payment.Status.REFUND_PENDING)
          service.refundSettled(id, provider.voidOrRefund(id));
        else if (p.status == Payment.Status.INITIATED)
          service.settle(id, provider.charge(id, p.amount, p.currency, p.scenario));
        log.info("Payment attempt completed status={}", service.snapshot(id).status);
      } catch (Exception e) {
        log.warn("Payment processing failed type={}", e.getClass().getSimpleName());
        service.providerUnavailable(id);
      }
    }
  }
}
