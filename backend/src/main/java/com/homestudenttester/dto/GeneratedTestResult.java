package com.homestudenttester.dto;

import java.time.Instant;
import java.util.List;

public record GeneratedTestResult(
    int questionCount,
    long elapsedSeconds,
    double averageSecondsPerQuestion,
    double earned,
    double possible,
    List<GeneratedWrongAnswer> wrongAnswers,
    Instant scoredAt,
    TokenUsage tokenUsage) {
}
