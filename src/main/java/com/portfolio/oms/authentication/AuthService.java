package com.portfolio.oms.authentication;

import com.portfolio.oms.common.BusinessException;
import com.portfolio.oms.customer.*;
import java.time.*;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final CustomerRepository customerRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtEncoder jwtEncoder;
  private final Clock clock;
  private final String issuer;
  private final Duration ttl;
  private final String dummyHash;

  public AuthService(CustomerRepository customerRepository,
                     PasswordEncoder passwordEncoder,
                     JwtEncoder jwtEncoder, Clock clock,
                     @Value("${app.jwt.issuer}") String issuer,
                     @Value("${app.jwt.ttl}") Duration ttl) {
    this.customerRepository = customerRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtEncoder = jwtEncoder;
    this.clock = clock;
    this.issuer = issuer;
    this.ttl = ttl;
    this.dummyHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
  }

  @Transactional
  public Token register(AuthController.Register register) {
    if (register.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException("Password exceeds BCrypt's 72-byte limit");
    
    String email = normalizeEmail(register.email());
    
    if (customerRepository.findByEmail(email).isPresent())
      throw BusinessException.conflict("EMAIL_EXISTS", "Email is already registered");
    
    Customer customer = new Customer();
    customer.email = email;
    customer.name = register.name().trim();
    customer.passwordHash = passwordEncoder.encode(register.password());
    
    customerRepository.saveAndFlush(customer);
    
    return token(customer);
  }

  public Token login(AuthController.Login r) {
    Customer customer = customerRepository.findByEmail(normalizeEmail(r.email())).orElse(null);

    boolean valid =
        r.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72
            && passwordEncoder.matches(r.password(), customer == null ? dummyHash : customer.passwordHash);
    if (customer == null || !valid || !customer.active)
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
    return token(customer);
  }

  public static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private Token token(Customer c) {
    var now = clock.instant();
    var claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(c.id.toString())
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .build();

    return new Token(
        jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue(),
        "Bearer",
        ttl.toSeconds());
  }

  public record Token(String accessToken, String tokenType, long expiresIn) {}
}
