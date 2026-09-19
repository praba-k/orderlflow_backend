package com.portfolio.oms.product;

import com.portfolio.oms.common.BusinessException;
import com.portfolio.oms.inventory.StockRepository;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class AvailabilityController {
  private final ProductRepository products;
  private final StockRepository stocks;

  public AvailabilityController(ProductRepository products, StockRepository stocks) {
    this.products = products;
    this.stocks = stocks;
  }

  public record Availability(int available, boolean purchasable) {}

  @GetMapping("/{id}/availability")
  @Transactional(readOnly = true)
  public Availability get(@PathVariable UUID id) {
    Product p = products.findById(id).orElseThrow(() -> BusinessException.missing("PRODUCT"));
    int available = stocks.findById(id).map(s -> s.available).orElse(0);
    return new Availability(available, p.status == Product.Status.ACTIVE && available > 0);
  }
}
