package com.portfolio.oms.discount;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coupons")
@PreAuthorize("hasRole('ADMIN')")
public class DiscountController {
  private final DiscountService service;

  public DiscountController(DiscountService s) {
    service = s;
  }

  public record Input(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,40}") String code,
      @NotNull Coupon.Type type,
      @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal value,
      @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal minimumAmount,
      @Positive @Digits(integer = 12, fraction = 2) BigDecimal maximumDiscount,
      @NotNull @Future Instant expiresAt,
      @Positive int usageLimit,
      @Positive int perCustomerLimit,
      UUID eligibleCustomerId) {}

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Coupon create(@Valid @RequestBody Input r) {
    return service.create(r);
  }

  @GetMapping
  public org.springframework.data.domain.Page<Coupon> list(
      org.springframework.data.domain.Pageable p) {
    return service.list(p);
  }

  @PutMapping("/{id}")
  public Coupon update(@PathVariable UUID id, @Valid @RequestBody Input r) {
    return service.save(id, r);
  }

  public record Active(boolean active) {}

  @PatchMapping("/{id}/active")
  public Coupon active(@PathVariable UUID id, @RequestBody Active r) {
    return service.active(id, r.active());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivate(@PathVariable UUID id) {
    service.deactivate(id);
  }
}
