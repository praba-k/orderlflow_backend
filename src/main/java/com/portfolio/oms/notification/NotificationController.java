package com.portfolio.oms.notification;

import com.portfolio.oms.authentication.CurrentCustomer;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
  private final NotificationService notificationService;
  private final CurrentCustomer currentCustomer;

  public NotificationController(NotificationService notificationService, CurrentCustomer currentCustomer) {
    this.notificationService = notificationService;
    this.currentCustomer = currentCustomer;
  }

  @GetMapping
  public Page<Notification> list(Pageable p) {
    return notificationService.list(currentCustomer.active().id, p);
  }

  @PatchMapping("/{id}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void read(@PathVariable UUID id) {
    notificationService.read(currentCustomer.active().id, id);
  }
}
