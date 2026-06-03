package com.homestudenttester.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestudenttester.config.AppProperties;
import com.homestudenttester.dto.GeneratedTestSubmission;
import com.homestudenttester.dto.OptionDto;
import com.homestudenttester.dto.QuestionBank;
import com.homestudenttester.dto.QuestionDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiServiceTest {

  @Test
  void scoresMultipleChoiceAndMultiSelectLocallyWithoutOpenAiKey() {
    OpenAiScorerService service = new OpenAiScorerService(testProperties(), new ObjectMapper());
    QuestionBank questionBank = new QuestionBank(
        "Objective Test",
        "Choose answers.",
        List.of(),
        List.of(
            new QuestionDto(
                "1",
                "multiple_choice",
                1,
                "What is 3 x 4?",
                List.of(new OptionDto("A", "7"), new OptionDto("B", "12"), new OptionDto("C", "13")),
                List.of(),
                List.of("B"),
                ""),
            new QuestionDto(
                "2",
                "multi_select",
                1,
                "Which equal 12?",
                List.of(new OptionDto("A", "3 x 4"), new OptionDto("B", "2 x 6"), new OptionDto("C", "5 + 8")),
                List.of(),
                List.of("A", "B"),
                "")));
    GeneratedTestSubmission submission = new GeneratedTestSubmission(
        "Student",
        Map.of(
            "question_1", "B",
            "question_2", List.of("B", "A")),
        30,
        Instant.parse("2026-06-02T00:00:00Z"));

    var result = service.scoreGeneratedTest(questionBank, submission);

    assertThat(result.earned()).isEqualTo(2);
    assertThat(result.possible()).isEqualTo(2);
    assertThat(result.wrongAnswers()).isEmpty();
    assertThat(result.tokenUsage()).isNull();
  }

  @Test
  void recordsWrongAnswerWhenMultiSelectHasExtraLabel() {
    OpenAiScorerService service = new OpenAiScorerService(testProperties(), new ObjectMapper());
    QuestionBank questionBank = new QuestionBank(
        "Objective Test",
        "Choose answers.",
        List.of(),
        List.of(new QuestionDto(
            "2",
            "multi_select",
            1,
            "Which equal 12?",
            List.of(new OptionDto("A", "3 x 4"), new OptionDto("B", "2 x 6"), new OptionDto("C", "5 + 8")),
            List.of(),
            List.of("A", "B"),
            "")));
    GeneratedTestSubmission submission = new GeneratedTestSubmission(
        "Student",
        Map.of("question_2", List.of("A", "B", "C")),
        30,
        Instant.parse("2026-06-02T00:00:00Z"));

    var result = service.scoreGeneratedTest(questionBank, submission);

    assertThat(result.earned()).isZero();
    assertThat(result.wrongAnswers()).hasSize(1);
    assertThat(result.wrongAnswers().get(0).studentAnswer()).isEqualTo("A, B, C");
    assertThat(result.wrongAnswers().get(0).expectedAnswer()).isEqualTo("A, B");
    assertThat(result.wrongAnswers().get(0).explanation()).isEqualTo("Extra: C.");
  }

  @Test
  void recordsHelpfulFeedbackWhenMultiSelectMissesALabel() {
    OpenAiScorerService service = new OpenAiScorerService(testProperties(), new ObjectMapper());
    QuestionBank questionBank = new QuestionBank(
        "Objective Test",
        "Choose answers.",
        List.of(),
        List.of(new QuestionDto(
            "2",
            "multi_select",
            1,
            "Which equal 12?",
            List.of(new OptionDto("A", "3 x 4"), new OptionDto("B", "2 x 6"), new OptionDto("C", "5 + 8")),
            List.of(),
            List.of("A", "B"),
            "")));
    GeneratedTestSubmission submission = new GeneratedTestSubmission(
        "Student",
        Map.of("question_2", List.of("B")),
        30,
        Instant.parse("2026-06-02T00:00:00Z"));

    var result = service.scoreGeneratedTest(questionBank, submission);

    assertThat(result.earned()).isZero();
    assertThat(result.wrongAnswers()).hasSize(1);
    assertThat(result.wrongAnswers().get(0).explanation()).isEqualTo("Missing: A.");
  }

  private AppProperties testProperties() {
    return new AppProperties(
        "",
        "https://api.openai.com/v1/responses",
        "gpt-5.4-mini",
        "gpt-5.5",
        8000,
        "low",
        false,
        "",
        List.of("amber"),
        List.of("fox"));
  }
}
