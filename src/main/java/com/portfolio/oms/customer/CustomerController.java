package com.portfolio.oms.customer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
  private final CustomerService service;

  public CustomerController(CustomerService s) {
    service = s;
  }

  public record ProfileInput(@NotBlank @Size(max = 120) String name) {}

  public record ActiveInput(boolean active) {}

  public record AddressInput(
      @NotBlank @Size(max = 120) String recipient,
      @NotBlank @Size(max = 200) String line1,
      @Size(max = 200) String line2,
      @NotBlank @Size(max = 100) String city,
      @NotBlank @Size(max = 20) String postalCode,
      @Pattern(regexp = "[A-Z]{2}") @NotNull String country) {}

  @GetMapping("/me")
  public CustomerService.Profile me() {
    return service.me();
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public org.springframework.data.domain.Page<CustomerService.Profile> list(
      org.springframework.data.domain.Pageable p) {
    return service.list(p);
  }

  @PutMapping("/me")
  public CustomerService.Profile update(@Valid @RequestBody ProfileInput r) {
    return service.update(r.name());
  }

  @PatchMapping("/{id}/active")
  @PreAuthorize("hasRole('ADMIN')")
  public CustomerService.Profile activate(@PathVariable UUID id, @RequestBody ActiveInput r) {
    return service.activate(id, r.active());
  }

  @GetMapping("/me/addresses")
  public List<Address> addresses() {
    return service.addresses();
  }

  @PatchMapping("/me/addresses/{id}/default")
  public Address makeDefault(@PathVariable UUID id) {
    return service.makeDefault(id);
  }

  @PostMapping("/me/addresses")
  @ResponseStatus(HttpStatus.CREATED)
  public Address add(@Valid @RequestBody AddressInput r) {
    return service.saveAddress(null, r);
  }

  @PutMapping("/me/addresses/{id}")
  public Address updateAddress(@PathVariable UUID id, @Valid @RequestBody AddressInput r) {
    return service.saveAddress(id, r);
  }

  @DeleteMapping("/me/addresses/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.deleteAddress(id);
  }
}
