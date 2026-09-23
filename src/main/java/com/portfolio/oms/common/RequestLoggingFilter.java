package com.portfolio.oms.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;

import org.jspecify.annotations.NonNull;
import org.slf4j.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-200)
public class RequestLoggingFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

  @Override
  protected void doFilterInternal(@NonNull HttpServletRequest httpServletRequest, HttpServletResponse res,
                                  FilterChain chain) throws ServletException, IOException {
    String requestId = UUID.randomUUID().toString();
    res.setHeader("X-Request-Id", requestId);
    long start = System.nanoTime();
    try (var scope = MDC.putCloseable("requestId", requestId)) {
      try {
        chain.doFilter(httpServletRequest, res);
      } finally {
        log.info(
            "HTTP method={} URI: {} status={} durationMs={}",
            httpServletRequest.getMethod(),
            httpServletRequest.getRequestURI(),
            res.getStatus(),
            (System.nanoTime() - start) / 1_000_000);
        MDC.remove("customerId");
      }
    }
  }
}
