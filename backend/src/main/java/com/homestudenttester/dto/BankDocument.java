package com.homestudenttester.dto;

import java.time.Instant;

public record BankDocument<T>(String rawMarkdown, T parsed, Instant createdAt) {
}
