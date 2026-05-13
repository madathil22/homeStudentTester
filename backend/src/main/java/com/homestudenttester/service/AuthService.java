package com.homestudenttester.service;

import org.springframework.stereotype.Service;

@Service
public class AuthService {
  public void requireAdmin(String token) {
    // Auth checks are currently disabled; this service is retained for future
    // control.
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
