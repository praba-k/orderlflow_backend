package com.portfolio.oms.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.oms.common.ApiErrors;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-190)
public class LoginRateLimitFilter extends OncePerRequestFilter {
  private static final DefaultRedisScript<Long> WINDOW =
      new DefaultRedisScript<>(
          "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end;"
              + " return n",
          Long.class);
  private final StringRedisTemplate redisTemplate;
  private final boolean isRedisEnabled;
  private final ObjectMapper objectMapper;

  public LoginRateLimitFilter(StringRedisTemplate redisTemplate,
                              @Value("${app.redis-enabled}") boolean isRedisEnabled,
                              ObjectMapper objectMapper) {
    this.redisTemplate = redisTemplate;
    this.isRedisEnabled = isRedisEnabled;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(@NonNull HttpServletRequest httpServletRequest) {
    return !isRedisEnabled
        || !httpServletRequest.getMethod().equals("POST")
        || !List.of("/api/auth/login", "/api/auth/register").contains(httpServletRequest.getServletPath());
  }

  @Override
  protected void doFilterInternal(HttpServletRequest httpServletRequest, @NonNull HttpServletResponse httpServletResponse,
                                  @NonNull FilterChain chain) throws ServletException, IOException {
    Long count;
    try {
      count = redisTemplate.execute(WINDOW, List.of("oms:auth:" + httpServletRequest.getRemoteAddr()));
    } catch (org.springframework.dao.DataAccessException e) {
      error(httpServletResponse, httpServletRequest, 503, "AUTH_LIMITER_UNAVAILABLE", "Authentication temporarily unavailable");
      return;
    }

    if (count == null) {
      error(httpServletResponse, httpServletRequest, 503, "AUTH_LIMITER_UNAVAILABLE", "Authentication temporarily unavailable");
      return;
    }

    if (count > 20) {
      httpServletResponse.setHeader("Retry-After", "60");
      error(httpServletResponse, httpServletRequest, 429, "RATE_LIMITED", "Too many authentication attempts");
      return;
    }
    chain.doFilter(httpServletRequest, httpServletResponse);
  }

  private void error(HttpServletResponse httpServletResponse, HttpServletRequest httpServletRequest,
                     int status, String code, String message) throws IOException {
    httpServletResponse.setStatus(status);
    httpServletResponse.setContentType("application/json");

    objectMapper.writeValue(
        httpServletResponse.getOutputStream(),
        new ApiErrors.ErrorBody(Instant.now(), status, code, message, httpServletRequest.getRequestURI())
    );
  }
}
