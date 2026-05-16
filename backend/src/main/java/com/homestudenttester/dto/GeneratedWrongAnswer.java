package com.homestudenttester.dto;

public record GeneratedWrongAnswer(
    String questionNumber,
    String prompt,
    String studentAnswer,
    String expectedAnswer,
    String explanation) {
}
