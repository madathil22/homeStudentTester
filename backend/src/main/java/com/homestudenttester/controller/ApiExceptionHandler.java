package com.homestudenttester.controller;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException error) {
    String message = error.getReason() == null ? "Request failed" : error.getReason();
    log.warn("Request failed with response status {}: {}", error.getStatusCode(), message, error);
    return ResponseEntity.status(error.getStatusCode()).body(Map.of("error", message));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException error) {
    log.warn("Bad request: {}", error.getMessage(), error);
    return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, String>> handleState(IllegalStateException error) {
    log.error("Request failed due to backend state: {}", error.getMessage(), error);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", error.getMessage()));
  }
}
