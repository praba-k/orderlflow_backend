package com.portfolio.oms.cart;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<Cart, UUID> {
  Optional<Cart> findByCustomerId(UUID id);
}
