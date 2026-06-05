package com.homestudenttester.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record VisualDto(
    String type,
    JsonNode data) {
}
