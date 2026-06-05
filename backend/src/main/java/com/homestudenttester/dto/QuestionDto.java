package com.homestudenttester.dto;

import java.util.List;

public record QuestionDto(
    String number,
    String type,
    double points,
    String prompt,
    VisualDto visual,
    List<OptionDto> options,
    List<String> passageIds,
    List<String> correctOptionLabels,
    String expectedAnswer) {
}
