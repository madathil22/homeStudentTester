package com.homestudenttester.dto;

import java.util.List;

public record QuestionBank(
    String title,
    String instructions,
    List<PassageDto> passages,
    List<QuestionDto> questions) {
}
