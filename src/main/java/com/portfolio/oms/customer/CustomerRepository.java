package com.portfolio.oms.customer;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
  Optional<Customer> findByEmail(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Customer c where c.id=:id")
  Optional<Customer> lockById(UUID id);
}
