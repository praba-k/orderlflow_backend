package com.portfolio.oms.authentication;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService service;

  public AuthController(AuthService s) {
    service = s;
  }

  public record Register(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 72) String password,
      @NotBlank @Size(max = 120) String name) {}

  public record Login(@NotBlank @Email String email, @NotBlank @Size(max = 72) String password) {}

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthService.Token register(@Valid @RequestBody Register r) {
    return service.register(r);
  }

  @PostMapping("/login")
  public AuthService.Token login(@Valid @RequestBody Login r) {
    return service.login(r);
  }
}
