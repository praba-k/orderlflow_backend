package com.portfolio.oms.customer;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, UUID> {
  List<Address> findByCustomerId(UUID customerId);

  Optional<Address> findByIdAndCustomerId(UUID id, UUID customerId);
}
