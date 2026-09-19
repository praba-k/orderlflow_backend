package com.portfolio.oms.order;

import com.portfolio.oms.authentication.CurrentCustomer;
import com.portfolio.oms.payment.PaymentProvider;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
  private final OrderService orders;
  private final CheckoutService checkout;
  private final CurrentCustomer current;

  public OrderController(OrderService o, CheckoutService c, CurrentCustomer u) {
    orders = o;
    checkout = c;
    current = u;
  }

  public record Checkout(
      @NotNull UUID addressId,
      @Pattern(regexp = "[A-Za-z0-9_-]{1,40}") String couponCode,
      @NotNull PaymentProvider.Scenario paymentScenario) {}

  public record Transition(@NotNull OrderStatus status) {}

  @PostMapping
  @PreAuthorize("hasRole('CUSTOMER')")
  @Operation(
      summary = "Checkout the current cart",
      description =
          "Requires Idempotency-Key. Creates a PENDING order and durable payment work. Replays"
              + " return the original purchase response; GET the order for current status. Mock"
              + " scenarios: SUCCESS, FAILED, TIMEOUT.")
  public ResponseEntity<OrderService.View> create(
      @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Checkout r) {
    var result = checkout.checkout(current.id(), key, r);
    return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED)
        .location(URI.create("/api/orders/" + result.view().order().id))
        .header("Idempotency-Replayed", Boolean.toString(result.replay()))
        .body(result.view());
  }

  @GetMapping
  public Page<PurchaseOrder> mine(Pageable p) {
    return orders.mine(p);
  }

  @GetMapping("/all")
  @PreAuthorize("hasRole('ADMIN')")
  public Page<PurchaseOrder> all(
      Pageable p,
      @RequestParam(required = false) OrderStatus status,
      @RequestParam(required = false) String search) {
    return orders.all(p, status, search);
  }

  @GetMapping("/{id}")
  public OrderService.View get(@PathVariable UUID id) {
    return orders.get(id);
  }

  @PostMapping("/{id}/cancel")
  public OrderService.View cancel(@PathVariable UUID id) {
    return orders.cancel(id);
  }

  @PostMapping("/{id}/status")
  @PreAuthorize("hasRole('ADMIN')")
  public OrderService.View advance(@PathVariable UUID id, @Valid @RequestBody Transition r) {
    return orders.advance(id, r.status());
  }
}
