package com.portfolio.oms.payment;

import static org.mockito.Mockito.*;

import com.portfolio.oms.order.*;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentWorkerTest {
  @Test
  void timeoutStaysAnExplicitUnknownOutcome() {
    var repo = mock(PaymentRepository.class);
    var service = mock(PaymentService.class);
    var provider = mock(PaymentProvider.class);
    Payment p = new Payment();
    p.orderId = UUID.randomUUID();
    p.amount = new java.math.BigDecimal("10");
    p.currency = "INR";
    p.scenario = PaymentProvider.Scenario.TIMEOUT;
    when(service.snapshot(p.id)).thenReturn(p);
    when(provider.charge(p.id, p.amount, p.currency, p.scenario))
        .thenReturn(PaymentProvider.Result.UNKNOWN);
    new PaymentWorker(
            repo,
            service,
            provider,
            mock(OrderRepository.class),
            mock(OrderService.class),
            Clock.systemUTC())
        .process(p.id);
    verify(service).settle(p.id, PaymentProvider.Result.UNKNOWN);
    verify(service, never()).refundSettled(any(), anyBoolean());
  }

  @Test
  void providerFailureIsDurablyRetried() {
    var service = mock(PaymentService.class);
    var provider = mock(PaymentProvider.class);
    Payment p = new Payment();
    p.orderId = UUID.randomUUID();
    when(service.snapshot(p.id)).thenReturn(p);
    when(provider.charge(any(), any(), any(), any())).thenThrow(new RuntimeException("offline"));
    new PaymentWorker(
            mock(PaymentRepository.class),
            service,
            provider,
            mock(OrderRepository.class),
            mock(OrderService.class),
            Clock.systemUTC())
        .process(p.id);
    verify(service).providerUnavailable(p.id);
  }
}
