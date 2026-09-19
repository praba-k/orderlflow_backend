package com.portfolio.oms.discount;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {
  Optional<Coupon> findByCode(String code);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Coupon c where c.id=:id")
  Optional<Coupon> lockById(UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Coupon c where c.code=:code")
  Optional<Coupon> lockByCode(String code);
}
