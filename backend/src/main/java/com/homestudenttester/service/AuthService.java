package com.homestudenttester.service;

import com.homestudenttester.config.AppProperties;
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
    if (!properties.adminToken().equals(token)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bad or missing admin token.");
    }
  }

  public void requireTest(String token) {
    if (!properties.testToken().equals(token)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bad or missing test token.");
    }
  }

  public void requireAny(String adminToken, String testToken) {
    boolean hasAdminToken = properties.adminToken().equals(adminToken);
    boolean hasTestToken = properties.testToken().equals(testToken);
    if (!hasAdminToken && !hasTestToken) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bad or missing secret token.");
    }
  }

  public String adminLink() {
    return "/admin/" + properties.adminToken();
  }

  public String resultsLink() {
    return "/results/" + properties.adminToken();
  }

  public String studentLink() {
    return "/take/" + properties.testToken();
  }
}
