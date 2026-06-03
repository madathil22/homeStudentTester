package com.homestudenttester.service;

import static com.homestudenttester.utils.ServiceUtils.extractContent;
import static com.homestudenttester.utils.ServiceUtils.normalizeJsonContent;
import static com.homestudenttester.utils.ServiceUtils.normalizeQuestionType;
import static com.homestudenttester.utils.ServiceUtils.normalizedCorrectionLabels;
import static com.homestudenttester.utils.ServiceUtils.validateCorrectionLabels;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestudenttester.config.AppProperties;
import com.homestudenttester.dto.AnswerKeyCorrectionRequest;
import com.homestudenttester.dto.AnswerKeyVerification;
import com.homestudenttester.dto.QuestionDto;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OpenAiFeedbackService {
  private static final Logger log = LoggerFactory.getLogger(OpenAiFeedbackService.class);

  private final AppProperties properties;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public OpenAiFeedbackService(AppProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  public AnswerKeyVerification verifyAnswerKeyCorrection(
      String subject,
      QuestionDto question,
      AnswerKeyCorrectionRequest correctionRequest) {
    String apiKey = properties.openAiKey();
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException(
          "OpenAI API key is not configured. Set OPENAI_API_KEY in .env or environment.");
    }
    if (question == null) {
      throw new IllegalArgumentException("Question is required for answer-key correction.");
    }
    AnswerKeyCorrectionRequest safeRequest = correctionRequest == null
        ? new AnswerKeyCorrectionRequest("", List.of(), "", "")
        : correctionRequest;
    String type = normalizeQuestionType(question.type());
    List<String> requestedLabels = normalizedCorrectionLabels(safeRequest.correctOptionLabels());
    String requestedExpectedAnswer = safeRequest.expectedAnswer() == null ? "" : safeRequest.expectedAnswer().trim();
    if ((type.equals("multiple_choice") || type.equals("multi_select")) && requestedLabels.isEmpty()) {
      throw new IllegalArgumentException("Correct option labels are required for objective questions.");
    }
    if (type.equals("free_text") && requestedExpectedAnswer.isBlank()) {
      throw new IllegalArgumentException("Expected answer is required for free-text questions.");
    }
    validateCorrectionLabels(question, type, requestedLabels);

    Map<String, Object> parentCorrection = new LinkedHashMap<>();
    parentCorrection.put("correctOptionLabels", requestedLabels);
    parentCorrection.put("expectedAnswer", requestedExpectedAnswer);
    parentCorrection.put("parentNote", safeRequest.parentNote() == null ? "" : safeRequest.parentNote().trim());
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("subject", subject == null ? "" : subject);
    payload.put("question", question);
    payload.put("parentCorrection", parentCorrection);
    payload.put(
        "instruction",
        "Verify whether the parent's proposed answer-key correction is academically correct. Approve only if the correction is clear and supported by the prompt/options.");

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", properties.openAiModel());
    body.put("input", buildVerificationSystemPrompt() + "\n\n" + writeJson(payload));
    body.put("text", Map.of("format", buildVerificationJsonSchemaFormat()));
    body.put("max_output_tokens", Math.min(properties.openAiMaxOutputTokens(), 1200));
    body.put("reasoning", Map.of("effort", properties.openAiReasoningEffort()));
    body.put("store", properties.openAiStore());

    try {
      log.info("Starting answer-key correction verification: questionNumber={}", question.number());
      String requestBody = objectMapper.writeValueAsString(body);
      long startedAt = System.nanoTime();
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(properties.openAiApiUrl()))
          .header("Authorization", "Bearer " + apiKey)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(requestBody))
          .timeout(Duration.ofSeconds(45))
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
      log.info("Answer-key correction verification response received: status={}, durationMs={}",
          response.statusCode(),
          durationMs);
      if (response.statusCode() != 200) {
        log.error("OpenAI correction verification returned non-200 response: status={}, body={}",
            response.statusCode(),
            response.body());
        throw new IllegalStateException("OpenAI API error: " + response.statusCode() + " " + response.body());
      }

      AnswerKeyVerification verification = parseAnswerKeyVerification(extractContent(objectMapper.readTree(response.body()), log));
      AnswerKeyVerification normalized = normalizeVerification(
          type,
          requestedLabels,
          requestedExpectedAnswer,
          verification);
      if (!normalized.approved()) {
        throw new IllegalStateException("Correction was not approved: " + normalized.reason());
      }
      return normalized;
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      log.error("OpenAI correction verification call was interrupted.", error);
      throw new IllegalStateException("Unable to verify answer-key correction with OpenAI API.", error);
    } catch (IOException error) {
      log.error("OpenAI correction verification call failed before a valid response was processed.", error);
      throw new IllegalStateException("Unable to verify answer-key correction with OpenAI API.", error);
    }
  }

  private String buildVerificationSystemPrompt() {
    return "You are an answer-key verification specialist. "
        + "You will receive a test question and a parent-proposed correction. "
        + "Approve only corrections that are academically correct and supported by the question prompt and options. "
        + "Return strict JSON only, with no markdown fences or commentary. "
        + "Use this exact response shape: "
        + "{"
        + "\"approved\":true,"
        + "\"correctOptionLabels\":[\"A\"],"
        + "\"expectedAnswer\":\"string\","
        + "\"reason\":\"string\","
        + "\"confidence\":\"high|medium|low\""
        + "}. "
        + "For objective questions, return the verified correctOptionLabels and an empty expectedAnswer. "
        + "For free_text questions, return an empty correctOptionLabels array and the verified expectedAnswer.";
  }

  private Map<String, Object> buildVerificationJsonSchemaFormat() {
    Map<String, Object> schema = Map.of(
        "type", "object",
        "additionalProperties", false,
        "properties", Map.of(
            "approved", Map.of("type", "boolean"),
            "correctOptionLabels", Map.of(
                "type", "array",
                "items", Map.of("type", "string")),
            "expectedAnswer", Map.of("type", "string"),
            "reason", Map.of("type", "string"),
            "confidence", Map.of("type", "string")),
        "required", List.of("approved", "correctOptionLabels", "expectedAnswer", "reason", "confidence"));
    return Map.of(
        "type", "json_schema",
        "name", "answer_key_correction_verification",
        "strict", true,
        "schema", schema);
  }

  private AnswerKeyVerification parseAnswerKeyVerification(String content) {
    try {
      return objectMapper.readValue(normalizeJsonContent(content), AnswerKeyVerification.class);
    } catch (IOException error) {
      log.error("Unable to parse answer-key verification JSON: {}", content, error);
      throw new IllegalStateException("OpenAI API returned invalid answer-key verification JSON.", error);
    }
  }

  private AnswerKeyVerification normalizeVerification(
      String type,
      List<String> requestedLabels,
      String requestedExpectedAnswer,
      AnswerKeyVerification verification) {
    if (verification == null) {
      throw new IllegalStateException("OpenAI API returned an empty answer-key verification.");
    }
    List<String> verifiedLabels = normalizedCorrectionLabels(verification.correctOptionLabels());
    String verifiedExpectedAnswer = verification.expectedAnswer() == null ? "" : verification.expectedAnswer().trim();
    String confidence = verification.confidence() == null ? "" : verification.confidence().trim().toLowerCase(Locale.ROOT);
    if (confidence.isBlank()) {
      confidence = "low";
    }
    boolean highEnoughConfidence = confidence.equals("high") || confidence.equals("medium");
    boolean approved = verification.approved() && highEnoughConfidence;
    if (type.equals("multiple_choice") || type.equals("multi_select")) {
      approved = approved && !verifiedLabels.isEmpty() && new HashSet<>(verifiedLabels).equals(new HashSet<>(requestedLabels));
      verifiedExpectedAnswer = "";
    } else if (type.equals("free_text")) {
      approved = approved && !verifiedExpectedAnswer.isBlank();
      verifiedLabels = List.of();
      if (verifiedExpectedAnswer.isBlank()) {
        verifiedExpectedAnswer = requestedExpectedAnswer;
      }
    }
    String reason = verification.reason() == null || verification.reason().isBlank()
        ? "The verifier did not provide a reason."
        : verification.reason().trim();
    return new AnswerKeyVerification(
        approved,
        verifiedLabels,
        verifiedExpectedAnswer,
        reason,
        confidence);
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (IOException error) {
      throw new IllegalStateException("Unable to write JSON payload.", error);
    }
  }

}
