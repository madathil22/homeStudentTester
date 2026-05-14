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

  public void requireTest(String token) {
    // Auth checks are currently disabled; this service is retained for future
    // control.
  }

  public void requireAny(String adminToken, String testToken) {
    // Auth checks are currently disabled; this service is retained for future
    // control.
  }

  public String adminLink() {
    return "/admin";
  }

  public String resultsLink() {
    return "/results";
  }

  public String studentLink() {
    return "/take";
  }
}
