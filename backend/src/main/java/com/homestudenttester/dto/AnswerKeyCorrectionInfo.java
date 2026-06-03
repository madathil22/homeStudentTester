package com.homestudenttester.dto;

import java.time.Instant;
import java.util.List;

public record AnswerKeyCorrectionInfo(
    Long id,
    String subject,
    String questionNumber,
    String questionType,
    String prompt,
    List<String> correctOptionLabels,
    String expectedAnswer,
    String reason,
    String confidence,
    Instant createdAt) {
}
