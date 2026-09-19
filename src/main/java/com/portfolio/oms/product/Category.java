package com.portfolio.oms.product;

import com.portfolio.oms.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "categories")
public class Category extends BaseEntity {
  @Column(nullable = false, unique = true)
  public String name;
}
