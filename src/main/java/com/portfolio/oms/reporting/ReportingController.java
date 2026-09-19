package com.portfolio.oms.reporting;

import java.time.Instant;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasRole('ADMIN')")
public class ReportingController {
  private final ReportingService service;

  public ReportingController(ReportingService s) {
    service = s;
  }

  @GetMapping("/revenue")
  public Map<String, Object> revenue(@RequestParam Instant from, @RequestParam Instant to) {
    return service.revenue(new ReportingService.Range(from, to));
  }

  @GetMapping("/orders")
  public List<Map<String, Object>> orders(@RequestParam Instant from, @RequestParam Instant to) {
    return service.orders(new ReportingService.Range(from, to));
  }

  @GetMapping("/top-products")
  public List<Map<String, Object>> top(
      @RequestParam Instant from,
      @RequestParam Instant to,
      @RequestParam(defaultValue = "10") int limit) {
    return service.topProducts(new ReportingService.Range(from, to), limit);
  }

  @GetMapping("/low-stock")
  public List<Map<String, Object>> low(
      @RequestParam(defaultValue = "10") int threshold,
      @RequestParam(defaultValue = "20") int limit) {
    return service.lowStock(threshold, limit);
  }

  @GetMapping("/failed-payments")
  public List<Map<String, Object>> failed(
      @RequestParam Instant from,
      @RequestParam Instant to,
      @RequestParam(defaultValue = "20") int limit) {
    return service.failedPayments(new ReportingService.Range(from, to), limit);
  }

  @GetMapping("/cancelled-orders")
  public List<Map<String, Object>> cancelled(
      @RequestParam Instant from,
      @RequestParam Instant to,
      @RequestParam(defaultValue = "20") int limit) {
    return service.cancelledOrders(new ReportingService.Range(from, to), limit);
  }
}
