package com.homestudenttester.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String openAiKey,
        String openAiApiUrl,
        String openAiModel,
        int openAiMaxTokens,
        double openAiTemperature,
        String adminPassword) {
}
