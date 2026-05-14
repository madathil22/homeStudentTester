package com.homestudenttester.dto;

import java.util.Map;

public record GeneratedTestSubmissionRequest(
    String studentName,
    Map<String, Object> answers,
    long elapsedSeconds) {
}
