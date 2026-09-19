package com.portfolio.oms.order;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
  List<OrderItem> findByOrderIdOrderByProductId(UUID orderId);
}
