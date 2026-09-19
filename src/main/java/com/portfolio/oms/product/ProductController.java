package com.portfolio.oms.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ProductController {
  private final ProductService service;

  public ProductController(ProductService s) {
    service = s;
  }

  public record Input(
      @NotBlank @Size(max = 200) String name,
      @Size(max = 4000) String description,
      @NotNull UUID categoryId,
      @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal price,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String sku,
      @NotNull Product.Status status) {}

  public record CategoryInput(@NotBlank @Size(max = 100) String name) {}

  @GetMapping("/products")
  public Page<Product> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) UUID category,
      @RequestParam(required = false) BigDecimal minPrice,
      @RequestParam(required = false) BigDecimal maxPrice,
      Pageable pageable) {
    return service.list(search, category, minPrice, maxPrice, pageable);
  }

  @GetMapping("/products/{id}")
  public Product get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/products")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public Product create(@Valid @RequestBody Input r) {
    return service.save(null, r);
  }

  @PutMapping("/products/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public Product update(@PathVariable UUID id, @Valid @RequestBody Input r) {
    return service.save(id, r);
  }

  @DeleteMapping("/products/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(@PathVariable UUID id) {
    service.deactivate(id);
  }

  @GetMapping("/categories")
  public List<Category> categories() {
    return service.categories();
  }

  @PostMapping("/categories")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('ADMIN')")
  public Category category(@Valid @RequestBody CategoryInput r) {
    return service.category(r.name());
  }
}
