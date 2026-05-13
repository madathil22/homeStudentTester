package com.homestudenttester.dto;

public record QuestionScore(
    String questionNumber,
    String type,
    double points,
    Double earned,
    String status,
    String expected,
    String explanation,
    String rubric,
    String sampleAnswer) {
}
