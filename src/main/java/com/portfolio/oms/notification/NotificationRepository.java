package com.portfolio.oms.notification;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
  Page<Notification> findByCustomerIdOrderByCreatedAtDesc(UUID customer, Pageable p);

  Optional<Notification> findByIdAndCustomerId(UUID id, UUID customer);
}
