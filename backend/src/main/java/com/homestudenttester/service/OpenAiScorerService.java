package com.homestudenttester.service;

import static com.homestudenttester.utils.ServiceUtils.answersByQuestionNumber;
import static com.homestudenttester.utils.ServiceUtils.extractContent;
import static com.homestudenttester.utils.ServiceUtils.firstPresent;
import static com.homestudenttester.utils.ServiceUtils.formatLabels;
import static com.homestudenttester.utils.ServiceUtils.logTokenUsage;
import static com.homestudenttester.utils.ServiceUtils.normalizeJsonContent;
import static com.homestudenttester.utils.ServiceUtils.normalizeQuestionType;
import static com.homestudenttester.utils.ServiceUtils.normalizedLabels;
import static com.homestudenttester.utils.ServiceUtils.numberValue;
import static com.homestudenttester.utils.ServiceUtils.parseTokenUsage;
import static com.homestudenttester.utils.ServiceUtils.questionFingerprint;
import static com.homestudenttester.utils.ServiceUtils.questionNumber;
import static com.homestudenttester.utils.ServiceUtils.readStringList;
import static com.homestudenttester.utils.ServiceUtils.sanitizeName;
import static com.homestudenttester.utils.ServiceUtils.submittedLabels;
import static com.homestudenttester.utils.ServiceUtils.textValue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestudenttester.config.AppProperties;
import com.homestudenttester.dto.GeneratedTestResult;
import com.homestudenttester.dto.GeneratedTestSubmission;
import com.homestudenttester.dto.GeneratedWrongAnswer;
import com.homestudenttester.dto.QuestionBank;
import com.homestudenttester.dto.QuestionDto;
import com.homestudenttester.dto.TokenUsage;
import com.homestudenttester.model.AnswerKeyCorrection;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OpenAiScorerService {
  private static final Logger log = LoggerFactory.getLogger(OpenAiScorerService.class);

  private final AppProperties properties;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public OpenAiScorerService(AppProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  public GeneratedTestResult scoreGeneratedTest(QuestionBank questionBank, GeneratedTestSubmission submission) {
    return scoreGeneratedTest(questionBank, submission, List.of());
  }

  public GeneratedTestResult scoreGeneratedTest(
      QuestionBank questionBank,
      GeneratedTestSubmission submission,
      List<AnswerKeyCorrection> corrections) {
    if (questionBank == null || questionBank.questions() == null || questionBank.questions().isEmpty()) {
      throw new IllegalArgumentException("Question bank is required for scoring.");
    }
    if (submission == null || submission.answers() == null) {
      throw new IllegalArgumentException("Submitted answers are required for scoring.");
    }

    QuestionBank correctedQuestionBank = applyAnswerKeyCorrections(questionBank, corrections);
    LocalScore localScore = scoreObjectiveQuestions(correctedQuestionBank, submission);
    GeneratedTestResult freeTextResult = localScore.freeTextQuestions().isEmpty()
        ? null
        : scoreFreeTextQuestions(correctedQuestionBank, localScore.freeTextQuestions(), submission);

    double possible = correctedQuestionBank.questions().stream().mapToDouble(QuestionDto::points).sum();
    double earned = localScore.earned() + (freeTextResult == null ? 0 : freeTextResult.earned());
    int questionCount = correctedQuestionBank.questions().size();
    long elapsedSeconds = Math.max(0, submission.elapsedSeconds());
    double averageSeconds = questionCount == 0 ? 0 : (double) elapsedSeconds / questionCount;
    List<GeneratedWrongAnswer> wrongAnswers = new ArrayList<>(localScore.wrongAnswers());
    if (freeTextResult != null && freeTextResult.wrongAnswers() != null) {
      wrongAnswers.addAll(freeTextResult.wrongAnswers());
    }

    return new GeneratedTestResult(
        questionCount,
        elapsedSeconds,
        averageSeconds,
        Math.max(0, Math.min(possible, earned)),
        possible,
        wrongAnswers,
        Instant.now(),
        freeTextResult == null ? null : freeTextResult.tokenUsage());
  }

  private GeneratedTestResult scoreFreeTextQuestions(
      QuestionBank questionBank,
      List<QuestionDto> freeTextQuestions,
      GeneratedTestSubmission submission) {
    String apiKey = properties.openAiKey();
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException(
          "OpenAI API key is not configured. Set OPENAI_API_KEY in .env or environment.");
    }
    QuestionBank freeTextQuestionBank = new QuestionBank(
        questionBank.title(),
        questionBank.instructions(),
        questionBank.passages(),
        freeTextQuestions);
    Map<String, Object> scoringPayload = new LinkedHashMap<>();
    scoringPayload.put("questionBank", freeTextQuestionBank);
    scoringPayload.put(
        "answersByQuestionNumber",
        answersByQuestionNumber(freeTextQuestionBank, submission.answers()));
    scoringPayload.put(
        "gradingInstruction",
        "Score only free-text answers. Use each question's expectedAnswer as the authoritative answer key and award partial credit only when answers demonstrate the requested knowledge.");
    String scoringInput = buildScoringSystemPrompt() + "\n\n" + writeJson(scoringPayload);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", properties.openAiModel());
    body.put("max_output_tokens", properties.openAiMaxOutputTokens());
    body.put("reasoning", Map.of("effort", properties.openAiReasoningEffort()));
    body.put("text", Map.of("format", buildScoringJsonSchemaFormat()));
    body.put("store", properties.openAiStore());
    body.put("input", scoringInput);

    try {
      log.info(
          "Starting OpenAI scoring: model={}, questionCount={}, answerCount={}",
          properties.openAiModel(),
          freeTextQuestionBank.questions().size(),
          submission.answers().size());
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
      log.info("OpenAI scoring response received: status={}, durationMs={}", response.statusCode(), durationMs);
      log.info("OpenAI scoring response body: {}", response.body());
      if (response.statusCode() != 200) {
        log.error("OpenAI scoring returned non-200 response: status={}, body={}", response.statusCode(),
            response.body());
        throw new IllegalStateException("OpenAI API error: " + response.statusCode() + " " + response.body());
      }

      JsonNode json = objectMapper.readTree(response.body());
      GeneratedTestResult aiResult = parseScoreResult(extractContent(json, log));
      TokenUsage usage = parseTokenUsage(json);
      logTokenUsage(log, "scoring", usage);
      log.info("OpenAI scoring completed successfully: earned={}, possible={}", aiResult.earned(), aiResult.possible());
      return normalizeScoreResult(freeTextQuestionBank, submission, aiResult, usage);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      log.error("OpenAI scoring call was interrupted.", error);
      throw new IllegalStateException("Unable to score generated test with OpenAI API.", error);
    } catch (IOException error) {
      log.error("OpenAI scoring call failed before a valid response was processed.", error);
      throw new IllegalStateException("Unable to score generated test with OpenAI API.", error);
    }
  }

  private QuestionBank applyAnswerKeyCorrections(QuestionBank questionBank, List<AnswerKeyCorrection> corrections) {
    if (corrections == null || corrections.isEmpty()) {
      return questionBank;
    }
    Map<String, AnswerKeyCorrection> correctionsByQuestion = new LinkedHashMap<>();
    List<AnswerKeyCorrection> newestFirst = new ArrayList<>(corrections);
    newestFirst.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
    for (AnswerKeyCorrection correction : newestFirst) {
      if (correction.isApproved()) {
        correctionsByQuestion.putIfAbsent(correction.getQuestionFingerprint(), correction);
      }
    }
    if (correctionsByQuestion.isEmpty()) {
      return questionBank;
    }

    List<QuestionDto> correctedQuestions = new ArrayList<>();
    for (QuestionDto question : questionBank.questions()) {
      AnswerKeyCorrection correction = correctionsByQuestion.get(questionFingerprint(question));
      if (correction == null) {
        correctedQuestions.add(question);
        continue;
      }
      List<String> correctedLabels = readStringList(objectMapper, log, correction.getCorrectOptionLabelsJson());
      String type = normalizeQuestionType(question.type());
      correctedQuestions.add(new QuestionDto(
          question.number(),
          question.type(),
          question.points(),
          question.prompt(),
          question.options(),
          question.passageIds(),
          type.equals("multiple_choice") || type.equals("multi_select") ? correctedLabels : question.correctOptionLabels(),
          type.equals("free_text") ? correction.getExpectedAnswer() : question.expectedAnswer()));
    }
    return new QuestionBank(
        questionBank.title(),
        questionBank.instructions(),
        questionBank.passages(),
        correctedQuestions);
  }

  private LocalScore scoreObjectiveQuestions(QuestionBank questionBank, GeneratedTestSubmission submission) {
    double earned = 0;
    List<GeneratedWrongAnswer> wrongAnswers = new ArrayList<>();
    List<QuestionDto> freeTextQuestions = new ArrayList<>();

    for (int index = 0; index < questionBank.questions().size(); index++) {
      QuestionDto question = questionBank.questions().get(index);
      String type = normalizeQuestionType(question.type());
      if (type.equals("free_text")) {
        freeTextQuestions.add(question);
        continue;
      }

      if (!type.equals("multiple_choice") && !type.equals("multi_select")) {
        freeTextQuestions.add(question);
        continue;
      }

      String number = questionNumber(question, index);
      Set<String> expectedLabels = normalizedLabels(question.correctOptionLabels());
      Set<String> submittedLabels = submittedLabels(submission.answers().get("question_" + sanitizeName(number)));
      if (submittedLabels.equals(expectedLabels)) {
        earned += Math.max(0, question.points());
        continue;
      }

      wrongAnswers.add(new GeneratedWrongAnswer(
          number,
          question.prompt(),
          formatLabels(submittedLabels),
          formatLabels(expectedLabels),
          objectiveExplanation(type, submittedLabels, expectedLabels)));
    }

    return new LocalScore(earned, wrongAnswers, freeTextQuestions);
  }

  private String objectiveExplanation(String type, Set<String> submittedLabels, Set<String> expectedLabels) {
    if (submittedLabels.isEmpty()) {
      return "No answer was selected.";
    }
    if (type.equals("multiple_choice")) {
      return "Correct option: " + formatLabels(expectedLabels) + ".";
    }

    Set<String> missingLabels = new HashSet<>(expectedLabels);
    missingLabels.removeAll(submittedLabels);
    Set<String> extraLabels = new HashSet<>(submittedLabels);
    extraLabels.removeAll(expectedLabels);
    List<String> parts = new ArrayList<>();
    if (!missingLabels.isEmpty()) {
      parts.add("Missing: " + formatLabels(missingLabels));
    }
    if (!extraLabels.isEmpty()) {
      parts.add("Extra: " + formatLabels(extraLabels));
    }
    if (parts.isEmpty()) {
      return "The selected options do not match the correct set.";
    }
    return String.join(". ", parts) + ".";
  }

  private String buildScoringSystemPrompt() {
    return "You are a strict but fair teacher scoring a student test submission. "
        + "You will receive one JSON object containing questionBank and answersByQuestionNumber. "
        + "Use answersByQuestionNumber as the authoritative student answer map. "
        + "Use each question's correctOptionLabels or expectedAnswer as the authoritative answer key. "
        + "Return strict JSON only, with no markdown fences or commentary. "
        + "Use this exact response shape: "
        + "{"
        + "\"earned\":0,"
        + "\"possible\":0,"
        + "\"wrongAnswers\":[{\"questionNumber\":\"1\",\"prompt\":\"string\",\"studentAnswer\":\"string\",\"expectedAnswer\":\"string\",\"explanation\":\"string\"}]"
        + "}. "
        + "earned and possible must be numeric totals. possible must equal the sum of the question points. "
        + "wrongAnswers must include every question that did not earn full credit and omit fully correct questions.";
  }

  private Map<String, Object> buildScoringJsonSchemaFormat() {
    Map<String, Object> schema = Map.of(
        "type", "object",
        "additionalProperties", false,
        "properties", Map.ofEntries(
            Map.entry("earned", Map.of("type", "number")),
            Map.entry("possible", Map.of("type", "number")),
            Map.entry("wrongAnswers", Map.of(
                "type", "array",
                "items", Map.of(
                    "type", "object",
                    "additionalProperties", false,
                    "properties", Map.ofEntries(
                        Map.entry("questionNumber", Map.of("type", "string")),
                        Map.entry("prompt", Map.of("type", "string")),
                        Map.entry("studentAnswer", Map.of("type", "string")),
                        Map.entry("expectedAnswer", Map.of("type", "string")),
                        Map.entry("explanation", Map.of("type", "string"))),
                    "required",
                    List.of("questionNumber", "prompt", "studentAnswer", "expectedAnswer", "explanation"))))),
        "required", List.of("earned", "possible", "wrongAnswers"));

    return Map.of(
        "type", "json_schema",
        "name", "test_grading_response",
        "strict", true,
        "schema", schema);
  }

  private GeneratedTestResult parseScoreResult(String content) {
    try {
      JsonNode root = objectMapper.readTree(normalizeJsonContent(content));
      JsonNode score = firstPresent(objectMapper, root, "result", "score", "scoring");
      if (score.isMissingNode() || !score.isObject()) {
        score = root;
      }
      return new GeneratedTestResult(
          0,
          0,
          0,
          numberValue(firstPresent(objectMapper, score, "earned", "score", "earnedPoints", "pointsEarned")),
          numberValue(firstPresent(objectMapper, score, "possible", "totalPossible", "maxScore", "totalPoints")),
          parseWrongAnswers(firstPresent(objectMapper, score, "wrongAnswers", "wrong_answers", "incorrectQuestions",
              "incorrect_questions", "missedQuestions", "missed_questions")),
          null,
          null);
    } catch (IOException error) {
      throw new IllegalStateException("OpenAI API returned invalid scoring JSON.", error);
    }
  }

  private List<GeneratedWrongAnswer> parseWrongAnswers(JsonNode node) {
    if (!node.isArray()) {
      return List.of();
    }

    List<GeneratedWrongAnswer> wrongAnswers = new ArrayList<>();
    for (JsonNode item : node) {
      if (!item.isObject()) {
        continue;
      }
      wrongAnswers.add(new GeneratedWrongAnswer(
          textValue(firstPresent(objectMapper, item, "questionNumber", "question_number", "number", "question")),
          textValue(firstPresent(objectMapper, item, "prompt", "questionPrompt", "question_prompt")),
          textValue(firstPresent(objectMapper, item, "studentAnswer", "student_answer", "answer", "submittedAnswer")),
          textValue(firstPresent(objectMapper, item, "expectedAnswer", "expected_answer", "expected", "correctAnswer")),
          textValue(firstPresent(objectMapper, item, "explanation", "reason", "feedback"))));
    }
    return wrongAnswers;
  }

  private GeneratedTestResult normalizeScoreResult(
      QuestionBank questionBank,
      GeneratedTestSubmission submission,
      GeneratedTestResult aiResult,
      TokenUsage usage) {
    double possible = questionBank.questions().stream().mapToDouble(QuestionDto::points).sum();
    double earned = Math.max(0, Math.min(possible, aiResult.earned()));
    int questionCount = questionBank.questions().size();
    long elapsedSeconds = Math.max(0, submission.elapsedSeconds());
    double averageSeconds = questionCount == 0 ? 0 : (double) elapsedSeconds / questionCount;
    return new GeneratedTestResult(
        questionCount,
        elapsedSeconds,
        averageSeconds,
        earned,
        possible,
        aiResult.wrongAnswers() == null ? List.of() : aiResult.wrongAnswers(),
        Instant.now(),
        usage);
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (IOException error) {
      throw new IllegalStateException("Unable to write JSON payload.", error);
    }
  }

  private record LocalScore(
      double earned,
      List<GeneratedWrongAnswer> wrongAnswers,
      List<QuestionDto> freeTextQuestions) {
  }

}
