package com.portfolio.oms.authentication;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.*;

class LoginRateLimitFilterTest {
  MockHttpServletRequest request() {
    var r = new MockHttpServletRequest("POST", "/api/auth/login");
    r.setServletPath("/api/auth/login");
    return r;
  }

  ObjectMapper mapper() {
    return new ObjectMapper().registerModule(new JavaTimeModule());
  }

  @Test
  void rejectsOverQuota() throws Exception {
    var redis = mock(StringRedisTemplate.class);
    when(redis.execute(
            any(org.springframework.data.redis.core.script.RedisScript.class), anyList()))
        .thenReturn(21L);
    var response = new MockHttpServletResponse();
    var chain = new MockFilterChain();
    new LoginRateLimitFilter(redis, true, mapper()).doFilter(request(), response, chain);
    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(response.getHeader("Retry-After")).isEqualTo("60");
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void dependencyFailureIsExplicit() throws Exception {
    var redis = mock(StringRedisTemplate.class);
    when(redis.execute(
            any(org.springframework.data.redis.core.script.RedisScript.class), anyList()))
        .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("offline"));
    var response = new MockHttpServletResponse();
    new LoginRateLimitFilter(redis, true, mapper())
        .doFilter(request(), response, new MockFilterChain());
    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getContentAsString()).contains("AUTH_LIMITER_UNAVAILABLE");
  }

  @Test
  void disabledLimiterDoesNotRequireRedis() throws Exception {
    var redis = mock(StringRedisTemplate.class);
    var chain = new MockFilterChain();
    new LoginRateLimitFilter(redis, false, mapper())
        .doFilter(request(), new MockHttpServletResponse(), chain);
    verifyNoInteractions(redis);
    assertThat(chain.getRequest()).isNotNull();
  }
}
