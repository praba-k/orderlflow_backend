package com.portfolio.oms.product;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;

class ProductServiceTest {
  @Test
  void rejectsUnapprovedSortProperties() {
    var service = new ProductService(mock(ProductRepository.class), mock(CategoryRepository.class));
    assertThatThrownBy(
            () ->
                service.list(null, null, null, null, PageRequest.of(0, 20, Sort.by("description"))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void validatesPriceAndSku() {
    try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
      var input =
          new ProductController.Input(
              "Product",
              null,
              java.util.UUID.randomUUID(),
              new java.math.BigDecimal("-1.00"),
              "bad sku",
              Product.Status.ACTIVE);
      assertThat(factory.getValidator().validate(input)).hasSize(2);
    }
  }
}
