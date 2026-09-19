package com.portfolio.oms.inventory;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface StockRepository extends JpaRepository<Stock, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Stock s where s.productId=:id")
  Optional<Stock> lockById(UUID id);
}
