package com.portfolio.oms.reporting;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportingService {
  private final JdbcTemplate jdbc;

  public ReportingService(JdbcTemplate j) {
    jdbc = j;
  }

  public record Range(Instant from, Instant to) {
    public Range {
      if (from == null
          || to == null
          || !from.isBefore(to)
          || Duration.between(from, to).compareTo(Duration.ofDays(366)) > 0)
        throw new IllegalArgumentException("Range must be positive and at most 366 days");
    }
  }

  public Map<String, Object> revenue(Range r) {
    var totals =
        jdbc.queryForMap(
            "select count(*) as paid_orders, coalesce(sum(o.total),0) as revenue,"
                + " coalesce(sum(o.tax),0) as tax, coalesce(sum(o.discount),0) as discounts from"
                + " orders o join payments p on p.order_id=o.id where p.status='SUCCESS' and"
                + " o.status<>'CANCELLED' and o.created_at>=? and o.created_at<?",
            java.sql.Timestamp.from(r.from()),
            java.sql.Timestamp.from(r.to()));
    var daily =
        jdbc.queryForList(
            "select (o.created_at at time zone 'UTC')::date as date, sum(o.total) as revenue from"
                + " orders o join payments p on p.order_id=o.id where p.status='SUCCESS' and"
                + " o.status<>'CANCELLED' and o.created_at>=? and o.created_at<? group by 1 order"
                + " by 1",
            java.sql.Timestamp.from(r.from()),
            java.sql.Timestamp.from(r.to()));
    return Map.of("currency", "INR", "totals", totals, "daily", daily);
  }

  public List<Map<String, Object>> orders(Range r) {
    return jdbc.queryForList(
        "select status,count(*) as total from orders where created_at>=? and created_at<? group by"
            + " status order by status",
        java.sql.Timestamp.from(r.from()),
        java.sql.Timestamp.from(r.to()));
  }

  public List<Map<String, Object>> topProducts(Range r, int limit) {
    checkLimit(limit);
    return jdbc.queryForList(
        "select i.product_id, max(i.product_name) as product_name,sum(i.quantity) as"
            + " units,sum(i.quantity*i.price_at_purchase) as gross_item_sales from order_items i"
            + " join orders o on o.id=i.order_id join payments p on p.order_id=o.id where"
            + " p.status='SUCCESS' and o.status<>'CANCELLED' and o.created_at>=? and o.created_at<?"
            + " group by i.product_id order by units desc,i.product_id limit ?",
        java.sql.Timestamp.from(r.from()),
        java.sql.Timestamp.from(r.to()),
        limit);
  }

  public List<Map<String, Object>> lowStock(int threshold, int limit) {
    checkLimit(limit);
    if (threshold < 0) throw new IllegalArgumentException("Negative threshold");
    return jdbc.queryForList(
        "select p.id,p.name,p.sku,coalesce(i.available,0) as available,coalesce(i.reserved,0) as"
            + " reserved from products p left join inventory i on i.product_id=p.id where"
            + " p.status='ACTIVE' and coalesce(i.available,0)<=? order by available,p.id limit ?",
        threshold,
        limit);
  }

  public List<Map<String, Object>> failedPayments(Range r, int limit) {
    checkLimit(limit);
    return jdbc.queryForList(
        "select id,order_id,status,last_error,attempts,created_at from payments where"
            + " status='FAILED' and created_at>=? and created_at<? order by created_at desc,id"
            + " limit ?",
        java.sql.Timestamp.from(r.from()),
        java.sql.Timestamp.from(r.to()),
        limit);
  }

  public List<Map<String, Object>> cancelledOrders(Range r, int limit) {
    checkLimit(limit);
    return jdbc.queryForList(
        "select id,customer_id,total,created_at from orders where status='CANCELLED' and"
            + " created_at>=? and created_at<? order by created_at desc,id limit ?",
        java.sql.Timestamp.from(r.from()),
        java.sql.Timestamp.from(r.to()),
        limit);
  }

  private void checkLimit(int n) {
    if (n < 1 || n > 100) throw new IllegalArgumentException("Limit must be 1-100");
  }
}
