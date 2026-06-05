package com.homestudenttester.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestudenttester.dto.OptionDto;
import com.homestudenttester.dto.QuestionDto;
import com.homestudenttester.dto.VisualDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServiceUtilsTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void questionFingerprintIncludesVisualData() throws Exception {
    QuestionDto pointAtTwo = questionWithNumberLinePoint(2);
    QuestionDto pointAtThree = questionWithNumberLinePoint(3);

    assertThat(ServiceUtils.questionFingerprint(pointAtTwo))
        .isNotEqualTo(ServiceUtils.questionFingerprint(pointAtThree));
  }

  @Test
  void questionFingerprintKeepsOptionOrderStable() throws Exception {
    QuestionDto orderedOptions = new QuestionDto(
        "1",
        "multiple_choice",
        1,
        "Which point is labeled A?",
        new VisualDto("number_line", objectMapper.readTree("{\"min\":0,\"max\":5,\"tickStep\":1}")),
        List.of(new OptionDto("A", "2"), new OptionDto("B", "3")),
        List.of(),
        List.of("A"),
        "");
    QuestionDto reversedOptions = new QuestionDto(
        "1",
        "multiple_choice",
        1,
        "Which point is labeled A?",
        new VisualDto("number_line", objectMapper.readTree("{\"min\":0,\"max\":5,\"tickStep\":1}")),
        List.of(new OptionDto("B", "3"), new OptionDto("A", "2")),
        List.of(),
        List.of("A"),
        "");

    assertThat(ServiceUtils.questionFingerprint(orderedOptions))
        .isEqualTo(ServiceUtils.questionFingerprint(reversedOptions));
  }

  private QuestionDto questionWithNumberLinePoint(int value) throws Exception {
    return new QuestionDto(
        "1",
        "multiple_choice",
        1,
        "Which point is labeled A?",
        new VisualDto(
            "number_line",
            objectMapper.readTree("""
                {
                  "min": 0,
                  "max": 5,
                  "tickStep": 1,
                  "points": [{ "label": "A", "value": %d }]
                }
                """.formatted(value))),
        List.of(new OptionDto("A", String.valueOf(value)), new OptionDto("B", "Not A")),
        List.of(),
        List.of("A"),
        "");
  }
}
