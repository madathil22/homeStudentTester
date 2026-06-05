package com.homestudenttester.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestudenttester.config.AppProperties;
import com.homestudenttester.dto.OptionDto;
import com.homestudenttester.dto.QuestionBank;
import com.homestudenttester.dto.QuestionDto;
import com.homestudenttester.dto.VisualDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiGeneratorServiceTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OpenAiGeneratorService service = new OpenAiGeneratorService(testProperties(), objectMapper);

  @Test
  void rendersNumberLineVisualFromStructuredData() throws Exception {
    VisualDto visual = new VisualDto(
        "number_line",
        objectMapper.readTree("""
            {
              "min": 0,
              "max": 10,
              "tickStep": 1,
              "points": [{ "label": "A", "value": 7 }],
              "jumps": [{ "from": 2, "to": 7, "label": "+5" }]
            }
            """));
    QuestionBank questionBank = new QuestionBank(
        "Number Line Test",
        "Choose carefully.",
        List.of(),
        List.of(new QuestionDto(
            "1",
            "multiple_choice",
            1,
            "Which point shows 7?",
            visual,
            List.of(new OptionDto("A", "7"), new OptionDto("B", "6")),
            List.of(),
            List.of("A"),
            "")));

    String html = service.renderQuestionBank(questionBank);

    assertThat(html).contains("class=\"number-line\"");
    assertThat(html).contains("aria-label=\"Number line from 0 to 10\"");
    assertThat(html).contains("<circle");
    assertThat(html).contains(">A</text>");
    assertThat(html).contains(">+5</text>");
    assertThat(html).contains("value=\"A\"");
  }

  @Test
  void escapesNumberLineLabelsAndDoesNotRenderRawModelMarkup() throws Exception {
    VisualDto visual = new VisualDto(
        "number_line",
        objectMapper.readTree("""
            {
              "min": 0,
              "max": 2,
              "tickStep": 1,
              "points": [{ "label": "<script>alert(1)</script>", "value": 1 }],
              "jumps": [{ "from": 0, "to": 1, "label": "<img src=x onerror=alert(1)>" }]
            }
            """));
    QuestionBank questionBank = new QuestionBank(
        "Safe Visual Test",
        "",
        List.of(),
        List.of(new QuestionDto(
            "1",
            "free_text",
            1,
            "Explain the point.",
            visual,
            List.of(),
            List.of(),
            List.of(),
            "The point is at 1.")));

    String html = service.renderQuestionBank(questionBank);

    assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    assertThat(html).contains("&lt;img src=x onerror=alert(1)&gt;");
    assertThat(html).doesNotContain("<script>");
    assertThat(html).doesNotContain("<img src=x");
  }

  @Test
  void ignoresUnsupportedVisualTypes() throws Exception {
    QuestionBank questionBank = new QuestionBank(
        "Unknown Visual Test",
        "",
        List.of(),
        List.of(new QuestionDto(
            "1",
            "free_text",
            1,
            "Answer the question.",
            new VisualDto("raw_svg", objectMapper.readTree("{\"svg\":\"<svg></svg>\"}")),
            List.of(),
            List.of(),
            List.of(),
            "Any complete answer.")));

    String html = service.renderQuestionBank(questionBank);

    assertThat(html).doesNotContain("question-visual");
    assertThat(html).doesNotContain("<svg></svg>");
  }

  private static AppProperties testProperties() {
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
