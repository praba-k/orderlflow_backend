package com.portfolio.oms.inventory;

import com.portfolio.oms.common.BusinessException;
import com.portfolio.oms.product.ProductRepository;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InventoryService {
  private final StockRepository stocks;
  private final InventoryTransactionRepository history;
  private final ProductRepository products;

  public InventoryService(
      StockRepository s, InventoryTransactionRepository h, ProductRepository p) {
    stocks = s;
    history = h;
    products = p;
  }

  public Stock get(UUID id) {
    if (!products.existsById(id)) throw BusinessException.missing("PRODUCT");
    return stocks
        .findById(id)
        .orElseGet(
            () -> {
              Stock s = new Stock();
              s.productId = id;
              return s;
            });
  }

  private Stock locked(UUID id) {
    products.lockById(id).orElseThrow(() -> BusinessException.missing("PRODUCT"));
    return stocks
        .lockById(id)
        .orElseGet(
            () -> {
              Stock s = new Stock();
              s.productId = id;
              return stocks.saveAndFlush(s);
            });
  }

  public Stock adjust(UUID id, int delta, String reason, UUID actor) {
    Stock s = locked(id);
    s.adjust(delta);
    audit(s, null, actor, "ADJUST", delta, reason);
    return s;
  }

  public void reserve(UUID id, int q, UUID order) {
    Stock s = locked(id);
    s.reserve(q);
    audit(s, order, null, "RESERVE", q, "Order checkout");
  }

  public void release(UUID id, int q, UUID order) {
    Stock s = locked(id);
    s.release(q);
    audit(s, order, null, "RELEASE", q, "Order cancelled");
  }

  public void consume(UUID id, int q, UUID order) {
    Stock s = locked(id);
    s.consume(q);
    audit(s, order, null, "CONSUME", q, "Payment confirmed");
  }

  public void restock(UUID id, int q, UUID order) {
    Stock s = locked(id);
    s.adjust(q);
    audit(s, order, null, "RESTOCK", q, "Confirmed order cancelled");
  }

  public Page<InventoryTransaction> history(UUID id, Pageable pageable) {
    return history.findByProductIdOrderByCreatedAtDesc(id, pageable);
  }

  private void audit(Stock s, UUID order, UUID actor, String operation, int q, String reason) {
    InventoryTransaction t = new InventoryTransaction();
    t.productId = s.productId;
    t.orderId = order;
    t.actorId = actor;
    t.operation = operation;
    t.quantity = q;
    t.reason = reason;
    t.availableAfter = s.available;
    t.reservedAfter = s.reserved;
    history.save(t);
  }
}
