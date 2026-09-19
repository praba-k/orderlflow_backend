package com.portfolio.oms.cart;

import com.portfolio.oms.common.BusinessException;
import com.portfolio.oms.customer.*;
import com.portfolio.oms.inventory.InventoryService;
import com.portfolio.oms.product.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CartService {
  private final CartRepository carts;
  private final CartItemRepository items;
  private final CustomerRepository customers;
  private final ProductRepository products;
  private final InventoryService inventory;

  public CartService(
      CartRepository c,
      CartItemRepository i,
      CustomerRepository u,
      ProductRepository p,
      InventoryService s) {
    carts = c;
    items = i;
    customers = u;
    products = p;
    inventory = s;
  }

  public Cart locked(UUID customer) {
    Customer c =
        customers.lockById(customer).orElseThrow(() -> BusinessException.missing("CUSTOMER"));
    if (!c.active) throw BusinessException.conflict("CUSTOMER_INACTIVE", "Customer is inactive");
    return carts
        .findByCustomerId(customer)
        .orElseGet(
            () -> {
              Cart cart = new Cart();
              cart.customerId = customer;
              return carts.saveAndFlush(cart);
            });
  }

  public View view(UUID customer) {
    return viewCart(locked(customer));
  }

  public View set(UUID customer, UUID product, int quantity, boolean add) {
    if (quantity < 1 || quantity > 1000)
      throw new IllegalArgumentException("Quantity must be between 1 and 1000");
    Cart cart = locked(customer);
    Product p = products.findById(product).orElseThrow(() -> BusinessException.missing("PRODUCT"));
    if (p.status != Product.Status.ACTIVE)
      throw BusinessException.conflict("PRODUCT_INACTIVE", "Inactive product");
    CartItem item =
        items
            .findByCartIdAndProductId(cart.id, product)
            .orElseGet(
                () -> {
                  CartItem i = new CartItem();
                  i.cartId = cart.id;
                  i.productId = product;
                  return i;
                });
    int target = add ? Math.addExact(item.quantity, quantity) : quantity;
    if (target > 1000 || target > inventory.get(product).available)
      throw BusinessException.conflict(
          "INSUFFICIENT_INVENTORY", "Cart quantity exceeds available inventory");
    if (item.quantity == 0 && items.findByCartIdOrderByProductId(cart.id).size() >= 100)
      throw BusinessException.conflict("CART_FULL", "Cart is limited to 100 distinct products");
    item.quantity = target;
    items.saveAndFlush(item);
    return viewCart(cart);
  }

  public void remove(UUID customer, UUID product) {
    Cart c = locked(customer);
    items.findByCartIdAndProductId(c.id, product).ifPresent(items::delete);
  }

  public void clear(UUID customer) {
    items.deleteByCartId(locked(customer).id);
  }

  public List<CartItem> checkoutItems(UUID customer) {
    return items.findByCartIdOrderByProductId(locked(customer).id);
  }

  private View viewCart(Cart c) {
    List<Line> lines =
        items.findByCartIdOrderByProductId(c.id).stream()
            .map(
                i -> {
                  Product p = products.findById(i.productId).orElseThrow();
                  return new Line(
                      i.productId,
                      p.name,
                      i.quantity,
                      p.price,
                      p.price.multiply(BigDecimal.valueOf(i.quantity)),
                      p.status);
                })
            .toList();
    return new View(
        c.id,
        lines,
        lines.stream().map(Line::subtotal).reduce(new BigDecimal("0.00"), BigDecimal::add));
  }

  public record Line(
      UUID productId,
      String name,
      int quantity,
      BigDecimal unitPrice,
      BigDecimal subtotal,
      Product.Status status) {}

  public record View(UUID id, List<Line> items, BigDecimal subtotal) {}
}
