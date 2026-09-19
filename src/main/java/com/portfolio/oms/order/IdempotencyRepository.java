package com.portfolio.oms.order;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, UUID> {
  Optional<IdempotencyRecord> findByCustomerIdAndRequestKey(UUID customer, String key);
}
