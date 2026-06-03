package com.homestudenttester.dto;

import java.util.List;

public record AnswerKeyCorrectionRequest(
    String questionNumber,
    List<String> correctOptionLabels,
    String expectedAnswer,
    String parentNote) {
}
