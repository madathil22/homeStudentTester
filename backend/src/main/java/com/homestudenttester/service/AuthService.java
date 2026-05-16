package com.homestudenttester.service;

import com.homestudenttester.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
  private final AppProperties properties;

  public AuthService(AppProperties properties) {
    this.properties = properties;
  }

  public void requireAdmin(String token) {
    String password = properties.adminPassword();
    if (password == null || password.isBlank()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ADMIN_PASSWORD is not configured.");
    }
    if (token == null || token.isBlank()
        || !MessageDigest.isEqual(
            token.getBytes(StandardCharsets.UTF_8),
            password.getBytes(StandardCharsets.UTF_8))) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin password is required.");
    }
  }

}
