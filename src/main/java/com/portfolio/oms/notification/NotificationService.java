package com.portfolio.oms.notification;

import com.portfolio.oms.common.BusinessException;
import com.portfolio.oms.order.*;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationService {
  private final NotificationRepository notifications;
  private final JdbcTemplate jdbc;

  public NotificationService(NotificationRepository n, JdbcTemplate j) {
    notifications = n;
    jdbc = j;
  }

  @EventListener
  public void onOrderChanged(OrderChanged e) {
    record(
        e.customerId(),
        e.orderId(),
        e.status() == OrderStatus.PENDING ? "ORDER_CREATED" : "ORDER_" + e.status());
  }

  public void record(UUID customer, UUID order, String type) {
    // The durable notification row is committed atomically with the business change.
    jdbc.update(
        "insert into notifications(id,customer_id,order_id,event_type,created_at,read) values"
            + " (?,?,?,?,now(),false) on conflict(order_id,event_type) do nothing",
        UUID.randomUUID(),
        customer,
        order,
        type);
  }

  public Page<Notification> list(UUID customer, Pageable p) {
    return notifications.findByCustomerIdOrderByCreatedAtDesc(customer, p);
  }

  public void read(UUID customer, UUID id) {
    notifications
            .findByIdAndCustomerId(id, customer)
            .orElseThrow(() -> BusinessException.missing("NOTIFICATION"))
            .read =
        true;
  }
}
