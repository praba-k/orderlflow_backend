package com.portfolio.oms.order;

import com.portfolio.oms.authentication.CurrentCustomer;
import com.portfolio.oms.customer.Customer;
import com.portfolio.oms.payment.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderActionsController {
  private final OrderService orders;
  private final PaymentRepository payments;
  private final CurrentCustomer current;

  public OrderActionsController(
      OrderService orders, PaymentRepository payments, CurrentCustomer current) {
    this.orders = orders;
    this.payments = payments;
    this.current = current;
  }

  public record Actions(
      boolean canCancel, List<OrderStatus> transitions, boolean canRetryPayment) {}

  @GetMapping("/{id}/actions")
  public Actions actions(@PathVariable UUID id) {
    var status = orders.get(id).order().status;
    var transitions = new ArrayList<OrderStatus>();
    if (current.active().role == Customer.Role.ADMIN) {
      if (status == OrderStatus.CONFIRMED) transitions.add(OrderStatus.PROCESSING);
      if (status == OrderStatus.PROCESSING) transitions.add(OrderStatus.SHIPPED);
      if (status == OrderStatus.SHIPPED) transitions.add(OrderStatus.DELIVERED);
    }
    boolean retry =
        payments
            .findByOrderId(id)
            .map(
                p ->
                    p.status == Payment.Status.INITIATED
                        || p.status == Payment.Status.REFUND_PENDING)
            .orElse(false);
    return new Actions(
        status == OrderStatus.PENDING || status == OrderStatus.CONFIRMED, transitions, retry);
  }
}
