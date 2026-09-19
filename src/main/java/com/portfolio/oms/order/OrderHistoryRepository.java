package com.portfolio.oms.order;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderHistoryRepository extends JpaRepository<OrderStatusHistory, UUID> {
  List<OrderStatusHistory> findByOrderIdOrderByCreatedAtAsc(UUID id);
}
