package com.portfolio.oms.order;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderObservability {
  private final MeterRegistry meters;
  private static final Logger log = LoggerFactory.getLogger(OrderObservability.class);

  public OrderObservability(MeterRegistry meters) {
    this.meters = meters;
  }

  @TransactionalEventListener
  public void committed(OrderChanged event) {
    meters.counter("orders.transitions", "status", event.status().name()).increment();
    log.atInfo()
        .addKeyValue("customerId", event.customerId().toString())
        .addKeyValue("orderId", event.orderId().toString())
        .log("Order transition committed status={}", event.status());
  }
}
