package com.portfolio.oms.product;

import com.portfolio.oms.common.BusinessException;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductService {
  private final ProductRepository products;
  private final CategoryRepository categories;

  public ProductService(ProductRepository p, CategoryRepository c) {
    products = p;
    categories = c;
  }

  @Transactional(readOnly = true)
  public Product get(UUID id) {
    return products.findById(id).orElseThrow(() -> BusinessException.missing("PRODUCT"));
  }

  public Product save(UUID id, ProductController.Input r) {
    if (!categories.existsById(r.categoryId())) throw BusinessException.missing("CATEGORY");
    Product p =
        id == null
            ? new Product()
            : products.lockById(id).orElseThrow(() -> BusinessException.missing("PRODUCT"));
    p.name = r.name().trim();
    p.description = r.description();
    p.categoryId = r.categoryId();
    p.price = r.price();
    p.sku = r.sku().trim().toUpperCase(Locale.ROOT);
    p.status = r.status();
    return products.save(p);
  }

  public void deactivate(UUID id) {
    Product p = products.lockById(id).orElseThrow(() -> BusinessException.missing("PRODUCT"));
    p.status = Product.Status.INACTIVE;
  }

  @Transactional(readOnly = true)
  public Page<Product> list(
      String search, UUID category, BigDecimal min, BigDecimal max, Pageable pageable) {
    for (Sort.Order s : pageable.getSort())
      if (!Set.of("name", "price", "createdAt", "id").contains(s.getProperty()))
        throw new IllegalArgumentException("Unsupported sort");
    Specification<Product> spec =
        (root, q, cb) -> cb.equal(root.get("status"), Product.Status.ACTIVE);
    if (search != null && !search.isBlank())
      spec =
          spec.and(
              (root, q, cb) ->
                  cb.like(
                      cb.lower(root.get("name")),
                      "%"
                          + search.toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_")
                          + "%",
                      '\\'));
    if (category != null)
      spec = spec.and((root, q, cb) -> cb.equal(root.get("categoryId"), category));
    if (min != null)
      spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min));
    if (max != null) spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("price"), max));
    return products.findAll(spec, pageable);
  }

  @Transactional(readOnly = true)
  public Page<Product> manage(String search, Product.Status status, Pageable pageable) {
    for (Sort.Order s : pageable.getSort())
      if (!Set.of("name", "price", "createdAt", "id", "status").contains(s.getProperty()))
        throw new IllegalArgumentException("Unsupported sort");
    Specification<Product> spec = (root, q, cb) -> cb.conjunction();
    if (status != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
    if (search != null && !search.isBlank())
      spec =
          spec.and(
              (root, q, cb) ->
                  cb.like(
                      cb.lower(root.get("name")),
                      "%"
                          + search.toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_")
                          + "%",
                      '\\'));
    return products.findAll(spec, pageable);
  }

  public List<Category> categories() {
    return categories.findAll(Sort.by("name"));
  }

  public Category category(String name) {
    Category c = new Category();
    c.name = name.trim();
    return categories.save(c);
  }
}
