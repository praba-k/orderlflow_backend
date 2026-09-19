package com.portfolio.oms.authentication;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AuthServiceTest {
  @Test
  void normalizesEmail() {
    assertThat(AuthService.normalizeEmail("  Buyer@EXAMPLE.COM ")).isEqualTo("buyer@example.com");
  }

  @Test
  void validatesRegistration() {
    try (var validator = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
      assertThat(validator.getValidator().validate(new AuthController.Register("bad", "short", "")))
          .hasSize(3);
    }
  }
}
