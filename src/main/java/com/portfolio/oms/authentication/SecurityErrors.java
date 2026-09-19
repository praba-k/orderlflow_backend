package com.portfolio.oms.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.oms.common.ApiErrors;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class SecurityErrors implements AuthenticationEntryPoint, AccessDeniedHandler {
  private final ObjectMapper objectMapper;

  public SecurityErrors(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response,
                       AuthenticationException e) throws IOException {
    response.setHeader("WWW-Authenticate", "Bearer");
    write(request, response, 401, "UNAUTHORIZED", "Valid authentication required");
  }

  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response,
                     AccessDeniedException e) throws IOException {
    write(request, response, 403, "FORBIDDEN", "Access denied");
  }

  private void write(HttpServletRequest request, HttpServletResponse response,
                     int status, String code, String message) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");

    objectMapper.writeValue(
        response.getOutputStream(),
        new ApiErrors.ErrorBody(Instant.now(), status, code, message, request.getRequestURI())
    );
  }
}
