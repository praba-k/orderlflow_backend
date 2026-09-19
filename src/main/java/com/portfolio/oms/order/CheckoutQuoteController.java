package com.portfolio.oms.order;

import com.portfolio.oms.authentication.CurrentCustomer;
import com.portfolio.oms.cart.CartService;
import com.portfolio.oms.common.BusinessException;
import com.portfolio.oms.discount.DiscountService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/checkout/quote")
@PreAuthorize("hasRole('CUSTOMER')")
public class CheckoutQuoteController {
  private final CartService carts;
  private final DiscountService discounts;
  private final PricingService pricing;
  private final CurrentCustomer current;

  public CheckoutQuoteController(
      CartService carts,
      DiscountService discounts,
      PricingService pricing,
      CurrentCustomer current) {
    this.carts = carts;
    this.discounts = discounts;
    this.pricing = pricing;
    this.current = current;
  }

  @GetMapping
  public PricingService.Totals quote(@RequestParam(required = false) String couponCode) {
    var cart = carts.view(current.active().id);
    if (cart.items().isEmpty()) throw BusinessException.invalid("EMPTY_CART", "Cart is empty");
    return pricing.calculate(
        cart.subtotal(), discounts.preview(couponCode, cart.subtotal(), current.id()));
  }
}
