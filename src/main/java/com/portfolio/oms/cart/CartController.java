package com.portfolio.oms.cart;

import com.portfolio.oms.authentication.CurrentCustomer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@PreAuthorize("hasRole('CUSTOMER')")
public class CartController {
  private final CartService service;
  private final CurrentCustomer current;

  public CartController(CartService s, CurrentCustomer c) {
    service = s;
    current = c;
  }

  public record ItemInput(@NotNull UUID productId, @Min(1) @Max(1000) int quantity) {}

  public record Quantity(@Min(1) @Max(1000) int quantity) {}

  @GetMapping
  public CartService.View get() {
    return service.view(current.id());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CartService.View create() {
    return service.view(current.id());
  }

  @PostMapping("/items")
  public CartService.View add(@Valid @RequestBody ItemInput r) {
    return service.set(current.id(), r.productId(), r.quantity(), true);
  }

  @PutMapping("/items/{id}")
  public CartService.View set(@PathVariable UUID id, @Valid @RequestBody Quantity r) {
    return service.set(current.id(), id, r.quantity(), false);
  }

  @DeleteMapping("/items/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@PathVariable UUID id) {
    service.remove(current.id(), id);
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void clear() {
    service.clear(current.id());
  }
}
