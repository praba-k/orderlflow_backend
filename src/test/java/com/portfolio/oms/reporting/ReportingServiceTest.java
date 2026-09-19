package com.portfolio.oms.reporting;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReportingServiceTest {
  @Test
  void boundsDateRange() {
    Instant now = Instant.now();
    assertThatThrownBy(() -> new ReportingService.Range(now, now))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ReportingService.Range(now, now.plusSeconds(368L * 86400)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
