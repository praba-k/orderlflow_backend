package com.portfolio.oms.authentication;

import com.portfolio.oms.common.BusinessException;
import com.portfolio.oms.customer.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentCustomer {
  private final CustomerRepository customerRepository;

  public CurrentCustomer(CustomerRepository customerRepository) {
    this.customerRepository = customerRepository;
  }

  public UUID id() {
    return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
  }

  public Customer active() {
    Customer customer = customerRepository.findById(id())
            .orElseThrow(() -> BusinessException.missing("CUSTOMER"));

    if (!customer.active)
      throw new BusinessException(HttpStatus.FORBIDDEN, "CUSTOMER_INACTIVE", "Customer is inactive");

    return customer;
  }

  public void ownerOrAdmin(UUID owner) {
    Customer c = active();
    if (!c.id.equals(owner) && c.role != Customer.Role.ADMIN)
      throw new org.springframework.security.access.AccessDeniedException("Not owner");
  }
}
