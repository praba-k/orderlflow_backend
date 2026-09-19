package com.portfolio.oms.inventory;

import com.portfolio.oms.authentication.CurrentCustomer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
public class InventoryController {
  private final InventoryService service;
  private final CurrentCustomer current;

  public InventoryController(InventoryService s, CurrentCustomer c) {
    service = s;
    current = c;
  }

  public record Adjustment(
      @Min(-1000000) @Max(1000000) int delta, @NotBlank @Size(max = 250) String reason) {}

  @GetMapping("/{id}")
  public Stock get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/{id}/adjustments")
  public Stock adjust(@PathVariable UUID id, @Valid @RequestBody Adjustment r) {
    return service.adjust(id, r.delta(), r.reason(), current.active().id);
  }

  @GetMapping("/{id}/history")
  public Page<InventoryTransaction> history(@PathVariable UUID id, Pageable p) {
    return service.history(id, p);
  }
}
