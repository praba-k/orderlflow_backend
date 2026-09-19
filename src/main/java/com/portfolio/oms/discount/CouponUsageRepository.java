package com.portfolio.oms.discount;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, UUID> {
  int countByCouponIdAndCustomerId(UUID coupon, UUID customer);
}
