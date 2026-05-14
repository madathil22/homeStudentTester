package com.homestudenttester.dto;

public record GeneratedTestMetadata(
    String subject,
    QuestionBank questionBank,
    GeneratedTestSubmission submission,
    GeneratedTestResult result) {
}
