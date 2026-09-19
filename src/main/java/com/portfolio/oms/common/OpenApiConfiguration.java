package com.portfolio.oms.common;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfiguration {
  @Bean
  OpenApiCustomizer errorDocumentation() {
    return api -> {
      var errorSchema =
          ModelConverters.getInstance()
              .resolveAsResolvedSchema(
                  new io.swagger.v3.core.converter.AnnotatedType(ApiErrors.ErrorBody.class)
                      .resolveAsRef(true));
      errorSchema.referencedSchemas.forEach(api.getComponents()::addSchemas);
      api.getPaths()
          .values()
          .forEach(
              path ->
                  path.readOperations()
                      .forEach(
                          operation -> {
                            java.util.Map.of(
                                    "400",
                                    "Invalid request fields",
                                    "401",
                                    "Authentication required",
                                    "403",
                                    "Insufficient permission",
                                    "404",
                                    "Resource not found",
                                    "409",
                                    "Business conflict or concurrent change",
                                    "422",
                                    "Business validation failed",
                                    "429",
                                    "Authentication rate limited",
                                    "500",
                                    "Unexpected server failure",
                                    "503",
                                    "Required dependency unavailable")
                                .forEach(
                                    (status, description) -> {
                                      operation
                                          .getResponses()
                                          .addApiResponse(
                                              status,
                                              new ApiResponse()
                                                  .description(description)
                                                  .content(
                                                      new Content()
                                                          .addMediaType(
                                                              "application/json",
                                                              new MediaType()
                                                                  .schema(errorSchema.schema))));
                                    });
                          }));
      api.getPaths()
          .forEach(
              (path, item) -> {
                if (path.startsWith("/api/auth/"))
                  item.readOperations()
                      .forEach(operation -> operation.setSecurity(java.util.List.of()));
                if (path.startsWith("/api/products") || path.equals("/api/categories"))
                  if (item.getGet() != null) item.getGet().setSecurity(java.util.List.of());
              });
      api.info(
          new io.swagger.v3.oas.models.info.Info()
              .title("Order Management Platform")
              .version("1.0")
              .description(
                  "Single-currency INR commerce API. Checkout is asynchronous. Prices and addresses"
                      + " are snapshotted; order and payment retries are idempotent. Reports use"
                      + " UTC [from,to) ranges."));
    };
  }
}
