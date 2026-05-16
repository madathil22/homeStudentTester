package com.homestudenttester.dto;

import java.time.Instant;
import java.util.Map;

public record GeneratedTestSubmission(
    String studentName,
    Map<String, Object> answers,
    long elapsedSeconds,
    Instant submittedAt) {
}
