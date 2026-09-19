package com.portfolio.oms.payment;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders/{orderId}/payment")
public class PaymentController {
  private final PaymentService service;

  public PaymentController(PaymentService s) {
    service = s;
  }

  @GetMapping
  public Payment get(@PathVariable UUID orderId) {
    return service.forOrder(orderId);
  }

  @PostMapping("/retry")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Payment retry(@PathVariable UUID orderId) {
    return service.retry(orderId);
  }
}
