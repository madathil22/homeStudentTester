package com.homestudenttester.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
                String openAiKey,
                String openAiApiUrl,
                String openAiModel,
                String openAiFallbackModel,
                int openAiMaxOutputTokens,
                String openAiReasoningEffort,
                boolean openAiStore,
                String adminPassword) {
}
