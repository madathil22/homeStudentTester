package com.homestudenttester.dto;

import java.time.Instant;
import java.util.Map;

public record SubmissionDto(
    String id,
    String studentName,
    Instant submittedAt,
    Map<String, Object> answers,
    ScoreResult score) {
}
