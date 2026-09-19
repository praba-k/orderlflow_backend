package com.portfolio.oms.product;

import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/manage/products")
@PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
public class CatalogManagementController {
  private final ProductService products;

  public CatalogManagementController(ProductService products) {
    this.products = products;
  }

  @GetMapping
  public Page<Product> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Product.Status status,
      Pageable p) {
    return products.manage(search, status, p);
  }
}
