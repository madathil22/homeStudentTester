package com.homestudenttester.dto;

public record TokenUsage(
    long inputTokens,
    long cachedInputTokens,
    long outputTokens,
    long reasoningTokens,
    long totalTokens) {
}
