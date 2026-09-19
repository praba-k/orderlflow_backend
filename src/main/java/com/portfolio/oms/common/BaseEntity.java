package com.portfolio.oms.common;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public abstract class BaseEntity {
  @Id public UUID id = UUID.randomUUID();

  @Column(nullable = false, updatable = false)
  public Instant createdAt = Instant.now();

  @Column(nullable = false)
  public Instant updatedAt = Instant.now();

  @Version public long version;

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }
}
