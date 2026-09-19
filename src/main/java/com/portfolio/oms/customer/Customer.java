package com.portfolio.oms.customer;

import com.portfolio.oms.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {
  @Column(nullable = false, unique = true)
  public String email;

  @Column(nullable = false)
  public String passwordHash;

  @Column(nullable = false)
  public String name;

  @Column(nullable = false)
  public boolean active = true;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public Role role = Role.CUSTOMER;

  public enum Role {
    CUSTOMER,
    ADMIN,
    INVENTORY_MANAGER
  }
}
