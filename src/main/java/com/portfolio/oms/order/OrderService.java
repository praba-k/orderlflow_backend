package com.portfolio.oms.order;

import com.portfolio.oms.authentication.CurrentCustomer;
import com.portfolio.oms.cart.*;
import com.portfolio.oms.common.BusinessException;
import com.portfolio.oms.customer.*;
import com.portfolio.oms.discount.DiscountService;
import com.portfolio.oms.inventory.InventoryService;
import com.portfolio.oms.product.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderService {
  private final CartService carts;
  private final ProductRepository products;
  private final AddressRepository addresses;
  private final InventoryService inventory;
  private final DiscountService discounts;
  private final PricingService pricing;
  private final OrderRepository orders;
  private final OrderItemRepository items;
  private final OrderHistoryRepository history;
  private final CurrentCustomer current;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final Duration ttl;

  public OrderService(
      CartService c,
      ProductRepository p,
      AddressRepository a,
      InventoryService i,
      DiscountService d,
      PricingService pr,
      OrderRepository o,
      OrderItemRepository oi,
      OrderHistoryRepository h,
      CurrentCustomer u,
      ApplicationEventPublisher e,
      Clock clock,
      @Value("${app.payment.reservation-ttl}") Duration ttl) {
    carts = c;
    products = p;
    addresses = a;
    inventory = i;
    discounts = d;
    pricing = pr;
    orders = o;
    items = oi;
    history = h;
    current = u;
    events = e;
    this.clock = clock;
    this.ttl = ttl;
  }

  public View create(UUID customer, UUID addressId, String coupon) {
    List<CartItem> cart = carts.checkoutItems(customer);
    if (cart.isEmpty()) throw BusinessException.invalid("EMPTY_CART", "Cart is empty");
    Address address =
        addresses
            .findByIdAndCustomerId(addressId, customer)
            .orElseThrow(() -> BusinessException.missing("ADDRESS"));
    PurchaseOrder order = new PurchaseOrder();
    order.customerId = customer;
    order.couponCode = coupon;
    order.reservationExpiresAt = clock.instant().plus(ttl);
    order.shippingAddress =
        String.join(
            "\n",
            address.recipient,
            address.line1,
            address.line2 == null ? "" : address.line2,
            address.city,
            address.postalCode,
            address.country);
    List<OrderItem> lines = new ArrayList<>();
    BigDecimal subtotal = new BigDecimal("0.00");
    // Fixed lock order is shared with inventory settlement and cancellation.
    for (CartItem c : cart) {
      Product p =
          products.lockById(c.productId).orElseThrow(() -> BusinessException.missing("PRODUCT"));
      if (p.status != Product.Status.ACTIVE)
        throw BusinessException.conflict("PRODUCT_INACTIVE", "Product is inactive");
      OrderItem line = new OrderItem();
      line.orderId = order.id;
      line.productId = p.id;
      line.productName = p.name;
      line.sku = p.sku;
      line.quantity = c.quantity;
      line.priceAtPurchase = p.price;
      lines.add(line);
      subtotal = subtotal.add(p.price.multiply(BigDecimal.valueOf(c.quantity)));
    }
    // Save before coupon usage; FK is deferred until transaction commit.
    BigDecimal discount = discounts.redeem(coupon, subtotal, customer, order.id);
    var total = pricing.calculate(subtotal, discount);
    order.subtotal = total.subtotal();
    order.discount = total.discount();
    order.tax = total.tax();
    order.total = total.total();
    order.taxRate = total.taxRate();
    orders.saveAndFlush(order);
    items.saveAll(lines);
    for (OrderItem line : lines) inventory.reserve(line.productId, line.quantity, order.id);
    carts.clear(customer);
    changed(order);
    return new View(order, lines, history.findByOrderIdOrderByCreatedAtAsc(order.id));
  }

  @Transactional(readOnly = true)
  public View get(UUID id) {
    PurchaseOrder o = orders.findById(id).orElseThrow(() -> BusinessException.missing("ORDER"));
    current.ownerOrAdmin(o.customerId);
    return view(o);
  }

  public Page<PurchaseOrder> mine(Pageable p) {
    return orders.findByCustomerIdOrderByCreatedAtDesc(current.active().id, p);
  }

  public Page<PurchaseOrder> all(Pageable p, OrderStatus status, String search) {
    return orders.search(status, search == null ? "" : search.trim().toLowerCase(Locale.ROOT), p);
  }

  public View cancel(UUID id) {
    PurchaseOrder o = lock(id);
    current.ownerOrAdmin(o.customerId);
    cancelLocked(o);
    return view(o);
  }

  public void expire(UUID id) {
    PurchaseOrder o = lock(id);
    if (o.status == OrderStatus.PENDING && !clock.instant().isBefore(o.reservationExpiresAt))
      cancelLocked(o);
  }

  private void cancelLocked(PurchaseOrder o) {
    if (o.status == OrderStatus.CANCELLED) return;
    OrderStatus previous = o.status;
    o.transition(OrderStatus.CANCELLED);
    for (OrderItem i : items.findByOrderIdOrderByProductId(o.id)) {
      if (previous == OrderStatus.PENDING) inventory.release(i.productId, i.quantity, o.id);
      else inventory.restock(i.productId, i.quantity, o.id);
    }
    changed(o);
  }

  public View advance(UUID id, OrderStatus next) {
    if (!Set.of(OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.DELIVERED).contains(next))
      throw BusinessException.conflict(
          "INVALID_ORDER_STATE", "Use payment or cancellation workflow");
    PurchaseOrder o = lock(id);
    o.transition(next);
    changed(o);
    return view(o);
  }

  public PurchaseOrder lock(UUID id) {
    return orders.lockById(id).orElseThrow(() -> BusinessException.missing("ORDER"));
  }

  public boolean paymentSucceeded(PurchaseOrder o) {
    if (o.status != OrderStatus.PENDING) return false;
    if (!clock.instant().isBefore(o.reservationExpiresAt)) {
      cancelLocked(o);
      return false;
    }
    o.transition(OrderStatus.CONFIRMED);
    for (OrderItem i : items.findByOrderIdOrderByProductId(o.id))
      inventory.consume(i.productId, i.quantity, o.id);
    changed(o);
    return true;
  }

  public void paymentFailed(PurchaseOrder o) {
    if (o.status == OrderStatus.PENDING) cancelLocked(o);
  }

  private View view(PurchaseOrder o) {
    return new View(
        o,
        items.findByOrderIdOrderByProductId(o.id),
        history.findByOrderIdOrderByCreatedAtAsc(o.id));
  }

  private void changed(PurchaseOrder o) {
    OrderStatusHistory h = new OrderStatusHistory();
    h.orderId = o.id;
    h.status = o.status;
    history.save(h);
    events.publishEvent(new OrderChanged(o.id, o.customerId, o.status));
  }

  public record View(
      PurchaseOrder order, List<OrderItem> items, List<OrderStatusHistory> history) {}
}
