package com.portfolio.oms.inventory;

import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {
  Page<InventoryTransaction> findByProductIdOrderByCreatedAtDesc(UUID id, Pageable pageable);
}
