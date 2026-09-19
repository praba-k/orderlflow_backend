package com.portfolio.oms.payment;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
  @Query("select p.orderId from Payment p where p.id=:id")
  Optional<UUID> findOrderId(UUID id);

  Optional<Payment> findByOrderId(UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Payment p where p.id=:id")
  Optional<Payment> lockById(UUID id);

  List<Payment> findTop100ByStatusInAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(
      Collection<Payment.Status> statuses, Instant now);
}
