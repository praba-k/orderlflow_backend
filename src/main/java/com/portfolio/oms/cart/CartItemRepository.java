package com.portfolio.oms.cart;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
  List<CartItem> findByCartIdOrderByProductId(UUID id);

  Optional<CartItem> findByCartIdAndProductId(UUID cartId, UUID productId);

  void deleteByCartId(UUID id);
}
