package com.homestudenttester.dto;

import java.time.Instant;
import java.util.Map;

public record ScoreResult(
    double earned,
    double possible,
    Map<String, QuestionScore> byQuestion,
    Instant scoredAt) {
}
