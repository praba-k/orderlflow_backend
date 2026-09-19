package com.portfolio.oms.customer;

import com.portfolio.oms.authentication.CurrentCustomer;
import com.portfolio.oms.common.BusinessException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CustomerService {
  private final CustomerRepository customers;
  private final AddressRepository addresses;
  private final CurrentCustomer current;

  public CustomerService(CustomerRepository c, AddressRepository a, CurrentCustomer u) {
    customers = c;
    addresses = a;
    current = u;
  }

  public Profile me() {
    return profile(current.active());
  }

  public org.springframework.data.domain.Page<Profile> list(
      org.springframework.data.domain.Pageable p) {
    return customers.findAll(p).map(this::profile);
  }

  public Profile update(String name) {
    Customer c = current.active();
    c.name = name.trim();
    return profile(customers.save(c));
  }

  public Profile activate(UUID id, boolean active) {
    Customer c = customers.lockById(id).orElseThrow(() -> BusinessException.missing("CUSTOMER"));
    c.active = active;
    return profile(c);
  }

  public List<Address> addresses() {
    return addresses.findByCustomerId(current.active().id);
  }

  public Address saveAddress(UUID id, CustomerController.AddressInput r) {
    UUID owner = current.active().id;
    customers.lockById(owner).orElseThrow();
    Address a =
        id == null
            ? new Address()
            : addresses
                .findByIdAndCustomerId(id, owner)
                .orElseThrow(() -> BusinessException.missing("ADDRESS"));
    a.customerId = owner;
    a.recipient = r.recipient();
    a.line1 = r.line1();
    a.line2 = r.line2();
    a.city = r.city();
    a.postalCode = r.postalCode();
    a.country = r.country();
    if (id == null && addresses.findByCustomerId(owner).isEmpty()) a.defaultAddress = true;
    return addresses.save(a);
  }

  public void deleteAddress(UUID id) {
    customers.lockById(current.active().id).orElseThrow();
    addresses.delete(
        addresses
            .findByIdAndCustomerId(id, current.active().id)
            .orElseThrow(() -> BusinessException.missing("ADDRESS")));
  }

  public Address makeDefault(UUID id) {
    UUID owner = current.active().id;
    customers.lockById(owner).orElseThrow();
    Address selected =
        addresses
            .findByIdAndCustomerId(id, owner)
            .orElseThrow(() -> BusinessException.missing("ADDRESS"));
    for (Address a : addresses.findByCustomerId(owner)) a.defaultAddress = false;
    addresses.flush();
    selected.defaultAddress = true;
    return selected;
  }

  private Profile profile(Customer c) {
    return new Profile(c.id, c.email, c.name, c.active, c.role);
  }

  public record Profile(UUID id, String email, String name, boolean active, Customer.Role role) {}
}
