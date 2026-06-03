package com.homestudenttester.dto;

import java.util.List;

public record AnswerKeyVerification(
    boolean approved,
    List<String> correctOptionLabels,
    String expectedAnswer,
    String reason,
    String confidence) {
}
