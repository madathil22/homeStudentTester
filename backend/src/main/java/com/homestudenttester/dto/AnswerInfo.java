package com.homestudenttester.dto;

import java.util.List;

public record AnswerInfo(
    String number,
    String answer,
    List<String> accepted,
    Double points,
    String explanation,
    String rubric,
    String sampleAnswer) {
}
