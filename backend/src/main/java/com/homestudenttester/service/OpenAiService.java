package com.homestudenttester.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestudenttester.dto.GeneratedTestDocument;
import com.homestudenttester.dto.GeneratedTestResult;
import com.homestudenttester.dto.GeneratedTestSubmission;
import com.homestudenttester.dto.GeneratedWrongAnswer;
import com.homestudenttester.config.AppProperties;
import com.homestudenttester.dto.OptionDto;
import com.homestudenttester.dto.PassageDto;
import com.homestudenttester.dto.QuestionBank;
import com.homestudenttester.dto.QuestionDto;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class OpenAiService {
    private static final Logger log = LoggerFactory.getLogger(OpenAiService.class);
    private static final Pattern QUESTION_COUNT_PATTERN = Pattern.compile(
            "(?i)\\b(\\d{1,2})\\s+(?:question|questions|problem|problems|item|items)\\b");

    private static final String TEST_TEMPLATE = """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>%s Test</title>
              <script>
                window.MathJax = {
                  tex: {
                    inlineMath: [['\\\\(', '\\\\)']],
                    displayMath: [['\\\\[', '\\\\]']]
                  },
                  chtml: {
                    scale: 1.05
                  }
                };
              </script>
              <script defer src="https://cdn.jsdelivr.net/npm/mathjax@4/tex-chtml.js"></script>
              <style>
                :root {
                  color-scheme: light;
                  font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                  color: #172033;
                  background: #f6f7fb;
                }

                * {
                  box-sizing: border-box;
                }

                body {
                  margin: 0;
                  background: #f6f7fb;
                }

                .test-shell {
                  width: min(920px, calc(100%% - 32px));
                  margin: 0 auto;
                  padding: 28px 0 40px;
                }

                .test-header {
                  position: sticky;
                  top: 0;
                  z-index: 2;
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  gap: 16px;
                  padding: 14px 0 18px;
                  background: #f6f7fb;
                  border-bottom: 1px solid #dfe3ee;
                }

                .test-header h1 {
                  margin: 0;
                  font-size: clamp(1.35rem, 2.5vw, 2rem);
                  line-height: 1.15;
                }

                .test-meta {
                  display: flex;
                  align-items: center;
                  gap: 10px;
                  flex-wrap: wrap;
                  justify-content: flex-end;
                }

                .timer {
                  min-width: 132px;
                  padding: 10px 12px;
                  border: 1px solid #c9d1e3;
                  border-radius: 8px;
                  background: #fff;
                  font-variant-numeric: tabular-nums;
                  font-weight: 700;
                  text-align: center;
                }

                .submit-button {
                  min-height: 42px;
                  padding: 0 18px;
                  border: 0;
                  border-radius: 8px;
                  background: #1c5f5a;
                  color: #fff;
                  font-weight: 700;
                  cursor: pointer;
                }

                .submit-button:disabled {
                  background: #667085;
                  cursor: default;
                }

                .submission-status {
                  width: 100%%;
                  margin: 0;
                  color: #1c5f5a;
                  font-weight: 700;
                  text-align: right;
                }

                .test-card {
                  margin-top: 22px;
                  padding: 28px;
                  border: 1px solid #dfe3ee;
                  border-radius: 8px;
                  background: #fff;
                  box-shadow: 0 16px 40px rgba(16, 24, 40, 0.08);
                }

                .test-card h2,
                .test-card h3 {
                  color: #24324d;
                }

                .test-instructions {
                  margin-top: 0;
                  color: #3f4b63;
                }

                .student-name {
                  margin-bottom: 22px;
                }

                .passage {
                  margin: 22px 0;
                  padding: 18px;
                  border-left: 4px solid #1c5f5a;
                  background: #f4faf8;
                }

                .passage h3 {
                  margin-top: 0;
                }

                .question {
                  margin-top: 24px;
                  padding-top: 22px;
                  border-top: 1px solid #dfe3ee;
                }

                .question:first-of-type {
                  border-top: 0;
                }

                .question-title {
                  display: flex;
                  align-items: baseline;
                  justify-content: space-between;
                  gap: 14px;
                  margin-bottom: 8px;
                }

                .question-title h3 {
                  margin: 0;
                }

                .points {
                  flex: 0 0 auto;
                  color: #526070;
                  font-size: 0.95rem;
                  font-weight: 700;
                }

                .question-prompt {
                  margin: 8px 0 14px;
                }

                mjx-container {
                  color: #172033;
                }

                mjx-container[display="true"] {
                  margin: 16px 0;
                  overflow-x: auto;
                  overflow-y: hidden;
                }

                .option {
                  display: flex;
                  align-items: flex-start;
                  gap: 10px;
                  margin: 10px 0;
                }

                .option input {
                  margin-top: 4px;
                }

                .test-card label {
                  display: block;
                  margin: 10px 0;
                }

                .test-card input[type="text"],
                .test-card textarea {
                  width: 100%%;
                  max-width: 100%%;
                  min-height: 40px;
                  margin-top: 6px;
                  padding: 9px 10px;
                  border: 1px solid #bdc6d9;
                  border-radius: 6px;
                  font: inherit;
                }

                .test-card textarea {
                  min-height: 96px;
                  resize: vertical;
                }

                @media (max-width: 640px) {
                  .test-header {
                    position: static;
                    align-items: flex-start;
                    flex-direction: column;
                  }

                  .test-meta {
                    width: 100%%;
                    justify-content: flex-start;
                  }

                  .submission-status {
                    text-align: left;
                  }

                  .test-card {
                    padding: 20px;
                  }
                }
              </style>
            </head>
            <body>
              <main class="test-shell">
                <header class="test-header">
                  <h1>%s Test</h1>
                  <div class="test-meta">
                    <output class="timer" id="elapsedTime" aria-live="polite">00:00:00</output>
                    <button class="submit-button" id="submitTest" type="submit" form="generatedTestForm">Submit Test</button>
                    <p class="submission-status" id="submissionStatus" hidden></p>
                  </div>
                </header>
                <form class="test-card" id="generatedTestForm">
            %s
                </form>
              </main>
              <script>
                (() => {
                  const startedAt = Date.now();
                  const timer = document.getElementById('elapsedTime');
                  const form = document.getElementById('generatedTestForm');
                  const submit = document.getElementById('submitTest');
                  const status = document.getElementById('submissionStatus');
                  let submitted = false;

                  function formatElapsed(totalSeconds) {
                    const hours = String(Math.floor(totalSeconds / 3600)).padStart(2, '0');
                    const minutes = String(Math.floor((totalSeconds %% 3600) / 60)).padStart(2, '0');
                    const seconds = String(totalSeconds %% 60).padStart(2, '0');
                    return `${hours}:${minutes}:${seconds}`;
                  }

                  function updateTimer() {
                    timer.textContent = formatElapsed(Math.floor((Date.now() - startedAt) / 1000));
                  }

                  updateTimer();
                  const timerId = window.setInterval(updateTimer, 1000);

                  function collectAnswers() {
                    const answers = {};
                    const data = new FormData(form);
                    for (const [key, value] of data.entries()) {
                      if (key === 'studentName') continue;
                      if (Object.prototype.hasOwnProperty.call(answers, key)) {
                        answers[key] = Array.isArray(answers[key]) ? [...answers[key], value] : [answers[key], value];
                      } else {
                        answers[key] = value;
                      }
                    }
                    return answers;
                  }

                  form.addEventListener('submit', async (event) => {
                    event.preventDefault();
                    if (submitted) return;
                    const studentName = String(new FormData(form).get('studentName') || '').trim();
                    if (!studentName) {
                      status.hidden = false;
                      status.textContent = 'Enter your name before submitting.';
                      return;
                    }
                    const elapsedSeconds = Math.floor((Date.now() - startedAt) / 1000);
                    submit.disabled = true;
                    submit.textContent = 'Scoring...';
                    status.hidden = false;
                    status.textContent = 'Submitting your test...';
                    try {
                      const testId = window.location.pathname.split('/').filter(Boolean).pop();
                      const response = await fetch(`/api/test/${encodeURIComponent(testId)}/submit`, {
                        method: 'POST',
                        headers: { 'content-type': 'application/json' },
                        body: JSON.stringify({ studentName, answers: collectAnswers(), elapsedSeconds })
                      });
                      if (!response.ok) {
                        const errorText = await response.text();
                        throw new Error(errorText || `Submit failed with status ${response.status}`);
                      }
                      const payload = await response.json();
                      submitted = true;
                      window.clearInterval(timerId);
                      updateTimer();
                      form.querySelectorAll('input, textarea, select, button').forEach((field) => {
                        field.disabled = true;
                      });
                      submit.textContent = 'Submitted';
                      const result = payload.result;
                      status.textContent = `Submitted after ${timer.textContent}. Score: ${result.earned}/${result.possible}.`;
                    } catch (error) {
                      submit.disabled = false;
                      submit.textContent = 'Submit Test';
                      status.textContent = error.message;
                    }
                  });
                })();
              </script>
            </body>
            </html>
            """;

    private final AppProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiService(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public GeneratedTestDocument generateTest(String subject) {
        String apiKey = properties.openAiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OpenAI API key is not configured. Set OPENAI_API_KEY in .env or environment.");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Test subject is required.");
        }

        String testRequest = subject.trim();
        OptionalInt requestedQuestionCount = requestedQuestionCount(testRequest);
        log.info(
                "Starting OpenAI test generation: model={}, requestedQuestionCount={}, request={}",
                properties.openAiModel(),
                requestedQuestionCount.isPresent() ? requestedQuestionCount.getAsInt() : "unspecified",
                testRequest);
        String prompt = buildUserPrompt(testRequest, requestedQuestionCount);
        Map<String, Object> body = Map.of(
                "model", properties.openAiModel(),
                "temperature", properties.openAiTemperature(),
                "max_tokens", properties.openAiMaxTokens(),
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", buildSystemPrompt()),
                        Map.of("role", "user", "content", prompt)));

        try {
            String requestBody = objectMapper.writeValueAsString(body);
            long startedAt = System.nanoTime();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.openAiApiUrl()))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            log.info("OpenAI generation response received: status={}, durationMs={}", response.statusCode(), durationMs);
            if (response.statusCode() != 200) {
                log.error("OpenAI generation returned non-200 response: status={}, body={}", response.statusCode(), response.body());
                throw new IllegalStateException("OpenAI API error: " + response.statusCode() + " " + response.body());
            }

            JsonNode json = objectMapper.readTree(response.body());
            String questionBankJson = extractContent(json).trim();
            if (questionBankJson.isBlank()) {
                log.error("OpenAI generation returned an empty question-bank payload.");
                throw new IllegalStateException("OpenAI API returned an empty question bank payload.");
            }
            log.info("Parsing OpenAI question-bank payload.");
            QuestionBank questionBank = parseQuestionBank(questionBankJson);
            log.info("Validating generated question bank: title={}, questionCount={}", questionBank.title(), questionBank.questions().size());
            validateQuestionBank(questionBank, requestedQuestionCount);
            log.info("OpenAI test generation completed successfully: questionCount={}", questionBank.questions().size());
            return new GeneratedTestDocument(
                    applyTestTemplate(questionBank.title(), renderQuestionBank(questionBank)),
                    questionBank);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            log.error("OpenAI generation call was interrupted.", error);
            throw new IllegalStateException("Unable to call OpenAI API.", error);
        } catch (IOException error) {
            log.error("OpenAI generation call failed before a valid response was processed.", error);
            throw new IllegalStateException("Unable to call OpenAI API.", error);
        }
    }

    public GeneratedTestResult scoreGeneratedTest(QuestionBank questionBank, GeneratedTestSubmission submission) {
        String apiKey = properties.openAiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "OpenAI API key is not configured. Set OPENAI_API_KEY in .env or environment.");
        }
        if (questionBank == null || questionBank.questions() == null || questionBank.questions().isEmpty()) {
            throw new IllegalArgumentException("Question bank is required for scoring.");
        }
        if (submission == null || submission.answers() == null) {
            throw new IllegalArgumentException("Submitted answers are required for scoring.");
        }

        Map<String, Object> scoringPayload = Map.of(
                "questionBank", questionBank,
                "submission", submission,
                "answersByQuestionNumber", answersByQuestionNumber(questionBank, submission.answers()),
                "answerKeyRequirement",
                "Infer the expected answer from the question content. Award partial credit only when the answer demonstrates the requested knowledge.");
        Map<String, Object> body = Map.of(
                "model", properties.openAiModel(),
                "temperature", 0,
                "max_tokens", properties.openAiMaxTokens(),
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", buildScoringSystemPrompt()),
                        Map.of("role", "user", "content", writeJson(scoringPayload))));

        try {
            log.info(
                    "Starting OpenAI scoring: model={}, questionCount={}, answerCount={}",
                    properties.openAiModel(),
                    questionBank.questions().size(),
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
            if (response.statusCode() != 200) {
                log.error("OpenAI scoring returned non-200 response: status={}, body={}", response.statusCode(), response.body());
                throw new IllegalStateException("OpenAI API error: " + response.statusCode() + " " + response.body());
            }

            JsonNode json = objectMapper.readTree(response.body());
            GeneratedTestResult aiResult = parseScoreResult(extractContent(json));
            log.info("OpenAI scoring completed successfully: earned={}, possible={}", aiResult.earned(), aiResult.possible());
            return normalizeScoreResult(questionBank, submission, aiResult);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            log.error("OpenAI scoring call was interrupted.", error);
            throw new IllegalStateException("Unable to score generated test with OpenAI API.", error);
        } catch (IOException error) {
            log.error("OpenAI scoring call failed before a valid response was processed.", error);
            throw new IllegalStateException("Unable to score generated test with OpenAI API.", error);
        }
    }

    private String buildSystemPrompt() {
        return "You are a reliable educational content specialist who writes credible curriculum-based assessment materials. "
                + "Generate only a JSON question bank for a well-structured test. "
                + "Do not include analysis, debugging notes, or any explanation of your process. "
                + "Avoid trivial or nonsensical question formats such as arithmetic fill-in-the-blank items like 'What do you get by adding 200 to (blank)'. "
                + "Use realistic subject knowledge, clear instructions, and a professional test layout. "
                + "Treat the teacher request as authoritative for subject, grade level, topic scope, and exact question count. "
                + "For mathematical expressions in titles, instructions, passages, prompts, and options, use TeX notation wrapped in \\\\( ... \\\\) for inline math and \\\\[ ... \\\\] for display math. "
                + "Prefer properly typeset expressions such as \\\\(f(x)=ax^2+bx+c\\\\) over plain-text ASCII math when math notation is needed. "
                + "Return strict JSON only, with no markdown fences or surrounding commentary. "
                + "Use this exact shape: "
                + "{"
                + "\"title\":\"string\","
                + "\"instructions\":\"string\","
                + "\"passages\":[{\"id\":\"p1\",\"title\":\"string\",\"body\":\"string\"}],"
                + "\"questions\":[{\"number\":\"1\",\"type\":\"multiple_choice|multi_select|free_text\",\"points\":1,\"prompt\":\"string\",\"options\":[{\"label\":\"A\",\"text\":\"string\"}],\"passageIds\":[\"p1\"]}]"
                + "}. "
                + "Only multiple_choice and multi_select questions may include options; free_text questions should use an empty options array. "
                + "Include directions and a mix of question types appropriate for the subject.";
    }

    private String buildUserPrompt(String testRequest, OptionalInt requestedQuestionCount) {
        String countInstruction = requestedQuestionCount.isPresent()
                ? "Generate exactly " + requestedQuestionCount.getAsInt() + " questions. "
                : "Generate an appropriate number of questions for the request. ";
        return "Create a ready-to-use JSON question bank from this teacher request: '" + testRequest + "'. "
                + "Honor every concrete constraint in the request, especially exact question count, grade level, and academic subject. "
                + "Make the test intent clear in the title and instructions so a student understands what knowledge is being assessed. "
                + countInstruction
                + "Use multiple_choice, multi_select, and free_text questions as appropriate to the subject. "
                + "Assign a score value in the points field for every question. "
                + "Use options only for multiple_choice or multi_select questions. "
                + "Return only JSON that matches the requested schema.";
    }

    private String buildScoringSystemPrompt() {
        return "You are a strict but fair teacher scoring a student test submission. "
                + "You will receive one JSON object containing questionBank and submission. "
                + "Use answersByQuestionNumber as the authoritative student answer map. "
                + "Question answer keys may be implicit in the question/options, so infer the best expected answer from the educational content. "
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

    private String extractContent(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("OpenAI API returned no content.");
        }
        JsonNode message = choices.get(0).path("message");
        if (message.isMissingNode()) {
            throw new IllegalStateException("OpenAI API response is missing message content.");
        }
        return message.path("content").asText("");
    }

    private String applyTestTemplate(String subject, String testContent) {
        String safeSubject = escapeHtml(subject == null || subject.isBlank() ? "Generated" : subject);
        return TEST_TEMPLATE.formatted(safeSubject, safeSubject, indentContent(testContent));
    }

    private QuestionBank parseQuestionBank(String content) {
        try {
            return objectMapper.readValue(normalizeJsonContent(content), QuestionBank.class);
        } catch (IOException error) {
            throw new IllegalStateException("OpenAI API returned invalid question bank JSON.", error);
        }
    }

    private GeneratedTestResult parseScoreResult(String content) {
        try {
            JsonNode root = objectMapper.readTree(normalizeJsonContent(content));
            JsonNode score = firstPresent(root, "result", "score", "scoring");
            if (score.isMissingNode() || !score.isObject()) {
                score = root;
            }
            return new GeneratedTestResult(
                    0,
                    0,
                    0,
                    numberValue(firstPresent(score, "earned", "score", "earnedPoints", "pointsEarned")),
                    numberValue(firstPresent(score, "possible", "totalPossible", "maxScore", "totalPoints")),
                    parseWrongAnswers(firstPresent(score, "wrongAnswers", "wrong_answers", "incorrectQuestions",
                            "incorrect_questions", "missedQuestions", "missed_questions")),
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
                    textValue(firstPresent(item, "questionNumber", "question_number", "number", "question")),
                    textValue(firstPresent(item, "prompt", "questionPrompt", "question_prompt")),
                    textValue(firstPresent(item, "studentAnswer", "student_answer", "answer", "submittedAnswer")),
                    textValue(firstPresent(item, "expectedAnswer", "expected_answer", "expected", "correctAnswer")),
                    textValue(firstPresent(item, "explanation", "reason", "feedback"))));
        }
        return wrongAnswers;
    }

    private JsonNode firstPresent(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return objectMapper.missingNode();
    }

    private double numberValue(JsonNode node) {
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isTextual()) {
            try {
                return Double.parseDouble(node.asText().replaceAll("[^0-9.+-]", ""));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String textValue(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isValueNode()) {
            return node.asText("");
        }
        return node.toString();
    }

    private GeneratedTestResult normalizeScoreResult(
            QuestionBank questionBank,
            GeneratedTestSubmission submission,
            GeneratedTestResult aiResult) {
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
                Instant.now());
    }

    private String normalizeJsonContent(String content) {
        String trimmed = content
                .replaceFirst("(?is)^\\s*```(?:json)?\\s*", "")
                .replaceFirst("(?is)\\s*```\\s*$", "")
                .trim();
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }

    private void validateQuestionBank(QuestionBank questionBank, OptionalInt requestedQuestionCount) {
        if (questionBank == null) {
            throw new IllegalStateException("OpenAI API returned an empty question bank.");
        }
        if (questionBank.title() == null || questionBank.title().isBlank()) {
            throw new IllegalStateException("OpenAI API returned a question bank without a title.");
        }
        if (questionBank.questions() == null || questionBank.questions().isEmpty()) {
            throw new IllegalStateException("OpenAI API returned no questions.");
        }
        if (requestedQuestionCount.isPresent() && questionBank.questions().size() != requestedQuestionCount.getAsInt()) {
            throw new IllegalStateException("OpenAI API returned " + questionBank.questions().size()
                    + " questions, but the request asked for exactly " + requestedQuestionCount.getAsInt() + ".");
        }
        for (QuestionDto question : questionBank.questions()) {
            if (question == null || question.prompt() == null || question.prompt().isBlank()) {
                throw new IllegalStateException("OpenAI API returned a question without prompt text.");
            }
            String type = normalizeQuestionType(question.type());
            if ((type.equals("multiple_choice") || type.equals("multi_select"))
                    && (question.options() == null || question.options().size() < 2)) {
                throw new IllegalStateException("OpenAI API returned an objective question without enough options.");
            }
            if (!type.equals("multiple_choice") && !type.equals("multi_select") && !type.equals("free_text")) {
                throw new IllegalStateException("OpenAI API returned unsupported question type: " + question.type());
            }
        }
    }

    private OptionalInt requestedQuestionCount(String testRequest) {
        Matcher matcher = QUESTION_COUNT_PATTERN.matcher(testRequest);
        if (!matcher.find()) {
            return OptionalInt.empty();
        }
        int count = Integer.parseInt(matcher.group(1));
        return count > 0 ? OptionalInt.of(count) : OptionalInt.empty();
    }

    private String renderQuestionBank(QuestionBank questionBank) {
        StringBuilder html = new StringBuilder();
        html.append("<label class=\"student-name\">Student name<input type=\"text\" name=\"studentName\" autocomplete=\"name\"></label>\n");
        if (questionBank.instructions() != null && !questionBank.instructions().isBlank()) {
            html.append("<p class=\"test-instructions\">")
                    .append(escapeHtml(questionBank.instructions()))
                    .append("</p>\n");
        }

        if (questionBank.passages() != null) {
            for (PassageDto passage : questionBank.passages()) {
                if (passage == null || isBlank(passage.body())) {
                    continue;
                }
                html.append("<section class=\"passage\" id=\"")
                        .append(escapeAttribute(passage.id()))
                        .append("\">\n");
                if (!isBlank(passage.title())) {
                    html.append("<h3>").append(escapeHtml(passage.title())).append("</h3>\n");
                }
                html.append("<p>").append(escapeMultilineText(passage.body())).append("</p>\n")
                        .append("</section>\n");
            }
        }

        int index = 1;
        for (QuestionDto question : questionBank.questions()) {
            html.append(renderQuestion(question, index));
            index++;
        }
        return html.toString().trim();
    }

    private String renderQuestion(QuestionDto question, int index) {
        String number = isBlank(question.number()) ? String.valueOf(index) : question.number().trim();
        String type = normalizeQuestionType(question.type());
        String inputName = "question_" + sanitizeName(number);
        StringBuilder html = new StringBuilder();
        html.append("<section class=\"question\" data-question-type=\"")
                .append(escapeAttribute(type))
                .append("\">\n")
                .append("<div class=\"question-title\"><h3>Question ")
                .append(escapeHtml(number))
                .append("</h3><span class=\"points\">")
                .append(formatPoints(question.points()))
                .append("</span></div>\n")
                .append("<p class=\"question-prompt\">")
                .append(escapeMultilineText(question.prompt()))
                .append("</p>\n");

        if (type.equals("multiple_choice") || type.equals("multi_select")) {
            String inputType = type.equals("multi_select") ? "checkbox" : "radio";
            if (question.options() != null) {
                for (OptionDto option : question.options()) {
                    html.append(renderOption(inputName, inputType, option));
                }
            }
        } else {
            html.append("<label>Answer<textarea name=\"")
                    .append(escapeAttribute(inputName))
                    .append("\"></textarea></label>\n");
        }

        html.append("</section>\n");
        return html.toString();
    }

    private String renderOption(String inputName, String inputType, OptionDto option) {
        String label = option == null || isBlank(option.label()) ? "" : option.label().trim();
        String text = option == null || option.text() == null ? "" : option.text().trim();
        String value = isBlank(label) ? text : label;
        return "<label class=\"option\"><input type=\""
                + escapeAttribute(inputType)
                + "\" name=\""
                + escapeAttribute(inputName)
                + "\" value=\""
                + escapeAttribute(value)
                + "\"><span><strong>"
                + escapeHtml(label)
                + "</strong>"
                + (isBlank(label) ? "" : ". ")
                + escapeHtml(text)
                + "</span></label>\n";
    }

    private String normalizeQuestionType(String type) {
        if (type == null || type.isBlank()) {
            return "free_text";
        }
        String normalized = type.trim().toLowerCase().replace('-', '_').replace(' ', '_');
        if (normalized.equals("short_answer") || normalized.equals("short_response") || normalized.equals("essay")
                || normalized.equals("text")) {
            return "free_text";
        }
        return normalized;
    }

    private String sanitizeName(String value) {
        String sanitized = value.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return sanitized.isBlank() ? "answer" : sanitized;
    }

    private Map<String, Object> answersByQuestionNumber(QuestionBank questionBank, Map<String, Object> answers) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (int index = 0; index < questionBank.questions().size(); index++) {
            QuestionDto question = questionBank.questions().get(index);
            String number = isBlank(question.number()) ? String.valueOf(index + 1) : question.number().trim();
            normalized.put(number, answers.get("question_" + sanitizeName(number)));
        }
        return normalized;
    }

    private String formatPoints(double points) {
        String value = points == Math.rint(points)
                ? Long.toString(Math.round(points))
                : Double.toString(points);
        return value + (points == 1 ? " point" : " points");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String indentContent(String content) {
        return "          " + content.replace("\n", "\n          ");
    }

    private String escapeAttribute(String value) {
        return escapeHtml(value == null ? "" : value);
    }

    private String escapeMultilineText(String value) {
        return escapeHtml(value == null ? "" : value).replace("\n", "<br>");
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to write JSON payload.", error);
        }
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
