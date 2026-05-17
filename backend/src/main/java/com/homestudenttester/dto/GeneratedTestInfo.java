package com.homestudenttester.dto;

public record GeneratedTestInfo(
        String id,
        String subject,
        String link,
        String createdAt,
        GeneratedTestResult result,
        TokenUsage generationUsage) {
}
