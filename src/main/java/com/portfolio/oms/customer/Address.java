package com.portfolio.oms.customer;

import com.portfolio.oms.common.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "addresses")
public class Address extends BaseEntity {
  @Column(nullable = false)
  public boolean defaultAddress;

  @Column(nullable = false)
  public UUID customerId;

  @Column(nullable = false)
  public String recipient;

  @Column(nullable = false)
  public String line1;

  public String line2;

  @Column(nullable = false)
  public String city;

  @Column(nullable = false)
  public String postalCode;

  @Column(nullable = false)
  public String country;
}
