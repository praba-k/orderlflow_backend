package com.portfolio.oms.order;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface OrderRepository extends JpaRepository<PurchaseOrder, UUID> {
  @Query(
      "select o from PurchaseOrder o where (:status is null or o.status = :status) and"
          + " lower(cast(o.id as string)) like concat('%' , :search, '%')")
  Page<PurchaseOrder> search(OrderStatus status, String search, Pageable p);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from PurchaseOrder o where o.id=:id")
  Optional<PurchaseOrder> lockById(UUID id);

  Page<PurchaseOrder> findByCustomerIdOrderByCreatedAtDesc(UUID id, Pageable p);

  List<PurchaseOrder> findTop100ByStatusAndReservationExpiresAtBefore(
      OrderStatus status, java.time.Instant before);
}
