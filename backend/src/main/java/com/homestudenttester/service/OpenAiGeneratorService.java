package com.homestudenttester.service;

import static com.homestudenttester.utils.ServiceUtils.escapeAttribute;
import static com.homestudenttester.utils.ServiceUtils.escapeHtml;
import static com.homestudenttester.utils.ServiceUtils.escapeMultilineText;
import static com.homestudenttester.utils.ServiceUtils.extractContent;
import static com.homestudenttester.utils.ServiceUtils.formatPoints;
import static com.homestudenttester.utils.ServiceUtils.indentContent;
import static com.homestudenttester.utils.ServiceUtils.isBlank;
import static com.homestudenttester.utils.ServiceUtils.logTokenUsage;
import static com.homestudenttester.utils.ServiceUtils.normalizeJsonContent;
import static com.homestudenttester.utils.ServiceUtils.normalizeQuestionType;
import static com.homestudenttester.utils.ServiceUtils.parseTokenUsage;
import static com.homestudenttester.utils.ServiceUtils.sanitizeName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestudenttester.config.AppProperties;
import com.homestudenttester.dto.AnswerKeyCorrectionInfo;
import com.homestudenttester.dto.GeneratedTestDocument;
import com.homestudenttester.dto.OptionDto;
import com.homestudenttester.dto.PassageDto;
import com.homestudenttester.dto.QuestionBank;
import com.homestudenttester.dto.QuestionDto;
import com.homestudenttester.dto.TokenUsage;
import com.homestudenttester.dto.VisualDto;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OpenAiGeneratorService {
  private static final Logger log = LoggerFactory.getLogger(OpenAiGeneratorService.class);
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

          .question-visual {
            margin: 14px 0 16px;
            overflow-x: auto;
          }

          .question-visual svg {
            display: block;
            max-width: 100%%;
            height: auto;
          }

          .number-line text {
            fill: #24324d;
            font: 13px Inter, ui-sans-serif, system-ui, sans-serif;
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

  public OpenAiGeneratorService(AppProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  public GeneratedTestDocument generateTest(String subject, List<AnswerKeyCorrectionInfo> correctionMemories) {
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
    String combinedInput = buildSystemPrompt() + "\n\n"
        + buildUserPrompt(testRequest, requestedQuestionCount, correctionMemories);
    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("model", properties.openAiModel());
    body.put("max_output_tokens", properties.openAiMaxOutputTokens());
    body.put("reasoning", Map.of("effort", properties.openAiReasoningEffort()));
    body.put("text", Map.of("format", Map.of("type", "json_object")));
    body.put("store", properties.openAiStore());
    body.put("input", combinedInput);

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
      log.info("OpenAI generation response body: {}", response.body());
      if (response.statusCode() != 200) {
        log.error("OpenAI generation returned non-200 response: status={}, body={}", response.statusCode(),
            response.body());
        throw new IllegalStateException("OpenAI API error: " + response.statusCode() + " " + response.body());
      }

      JsonNode json = objectMapper.readTree(response.body());
      String questionBankJson = extractContent(json, log).trim();
      if (questionBankJson.isBlank()) {
        log.error("OpenAI generation returned an empty question-bank payload.");
        throw new IllegalStateException("OpenAI API returned an empty question bank payload.");
      }
      QuestionBank questionBank = parseQuestionBank(questionBankJson);
      validateQuestionBank(questionBank, requestedQuestionCount);
      TokenUsage usage = parseTokenUsage(json);
      logTokenUsage(log, "generation", usage);
      return new GeneratedTestDocument(
          applyTestTemplate(questionBank.title(), renderQuestionBank(questionBank)),
          questionBank,
          usage);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      log.error("OpenAI generation call was interrupted.", error);
      throw new IllegalStateException("Unable to call OpenAI API.", error);
    } catch (IOException error) {
      log.error("OpenAI generation call failed before a valid response was processed.", error);
      throw new IllegalStateException("Unable to call OpenAI API.", error);
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
        + "\"questions\":[{\"number\":\"1\",\"type\":\"multiple_choice|multi_select|free_text\",\"points\":1,\"prompt\":\"string\",\"visual\":{\"type\":\"number_line\",\"data\":{\"min\":0,\"max\":10,\"tickStep\":1,\"points\":[{\"label\":\"A\",\"value\":7}],\"jumps\":[{\"from\":2,\"to\":7,\"label\":\"+5\"}]}},\"options\":[{\"label\":\"A\",\"text\":\"string\"}],\"passageIds\":[\"p1\"],\"correctOptionLabels\":[\"A\"],\"expectedAnswer\":\"string\"}]"
        + "}. "
        + "Use visual only when a diagram is instructionally useful; otherwise use null. For number_line visuals, provide numeric min, max, tickStep, optional points, and optional jumps. "
        + "For multiple_choice, provide exactly one correctOptionLabels entry and an empty expectedAnswer. For multi_select, provide every correct option label and an empty expectedAnswer. For free_text, use an empty options array, an empty correctOptionLabels array, and a concise expectedAnswer. "
        + "Include directions and a mix of question types appropriate for the subject.";
  }

  private String buildUserPrompt(
      String testRequest,
      OptionalInt requestedQuestionCount,
      List<AnswerKeyCorrectionInfo> correctionMemories) {
    String countInstruction = requestedQuestionCount.isPresent()
        ? "Generate exactly " + requestedQuestionCount.getAsInt() + " questions. "
        : "Generate an appropriate number of questions for the request. ";
    return "Create a ready-to-use JSON question bank from this teacher request: '" + testRequest + "'. "
        + "Honor every concrete constraint in the request, especially exact question count, grade level, and academic subject. "
        + "Make the test intent clear in the title and instructions so a student understands what knowledge is being assessed. "
        + countInstruction
        + "Use multiple_choice, multi_select, and free_text questions as appropriate to the subject. "
        + "Assign a score value in the points field for every question. "
        + "When a number line would make the problem clearer, include a number_line visual instead of describing the line only in prose. "
        + "Use options only for multiple_choice or multi_select questions. Make sure every multiple_choice question has exactly one correct answer, every multi_select question has more than one correct answer, and no option set contains duplicate answer text. "
        + answerKeyMemoryInstruction(correctionMemories)
        + "Return only JSON that matches the requested schema. "
        + "Return only the final JSON. Do not explain your reasoning. Keep calculations concise.";
  }

  private String answerKeyMemoryInstruction(List<AnswerKeyCorrectionInfo> correctionMemories) {
    if (correctionMemories == null || correctionMemories.isEmpty()) {
      return "";
    }
    StringBuilder instruction = new StringBuilder();
    instruction.append("Use these parent-approved prior answer-key corrections to avoid repeating mistakes. ");
    for (AnswerKeyCorrectionInfo correction : correctionMemories) {
      instruction.append("Prior correction: prompt='")
          .append(correction.prompt())
          .append("', correctOptionLabels=")
          .append(correction.correctOptionLabels())
          .append(", expectedAnswer='")
          .append(correction.expectedAnswer())
          .append("', reason='")
          .append(correction.reason())
          .append("'. ");
    }
    return instruction.toString();
  }

  private QuestionBank parseQuestionBank(String content) {
    try {
      return objectMapper.readValue(normalizeJsonContent(content), QuestionBank.class);
    } catch (IOException error) {
      throw new IllegalStateException("OpenAI API returned invalid question bank JSON.", error);
    }
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
      validateAnswerMetadata(question, type);
    }
  }

  private void validateAnswerMetadata(QuestionDto question, String type) {
    List<OptionDto> options = question.options() == null ? List.of() : question.options();
    List<String> correctLabels = question.correctOptionLabels() == null ? List.of() : question.correctOptionLabels();
    String expectedAnswer = question.expectedAnswer() == null ? "" : question.expectedAnswer().trim();

    Set<String> optionLabels = new HashSet<>();
    Set<String> optionTexts = new HashSet<>();
    for (OptionDto option : options) {
      if (option == null || isBlank(option.label()) || isBlank(option.text())) {
        throw new IllegalStateException("OpenAI API returned an option without both label and text.");
      }
      String label = option.label().trim();
      String text = option.text().trim();
      if (!optionLabels.add(label)) {
        throw new IllegalStateException("OpenAI API returned duplicate option label: " + label + ".");
      }
      if (!optionTexts.add(text)) {
        throw new IllegalStateException("OpenAI API returned duplicate option text.");
      }
    }

    Set<String> uniqueCorrectLabels = new HashSet<>();
    for (String label : correctLabels) {
      if (isBlank(label)) {
        throw new IllegalStateException("OpenAI API returned a blank correct option label.");
      }
      String trimmed = label.trim();
      if (!uniqueCorrectLabels.add(trimmed)) {
        throw new IllegalStateException("OpenAI API returned duplicate correct option label: " + trimmed + ".");
      }
      if (!optionLabels.contains(trimmed)) {
        throw new IllegalStateException("OpenAI API returned a correct option label that is not present: " + trimmed + ".");
      }
    }

    if (type.equals("multiple_choice")) {
      if (correctLabels.size() != 1) {
        throw new IllegalStateException("OpenAI API returned a multiple-choice question without exactly one correct option.");
      }
      if (!expectedAnswer.isBlank()) {
        throw new IllegalStateException("OpenAI API returned expectedAnswer for a multiple-choice question.");
      }
      return;
    }

    if (type.equals("multi_select")) {
      if (correctLabels.size() < 2) {
        throw new IllegalStateException("OpenAI API returned a multi-select question with fewer than two correct options.");
      }
      if (!expectedAnswer.isBlank()) {
        throw new IllegalStateException("OpenAI API returned expectedAnswer for a multi-select question.");
      }
      return;
    }

    if (!options.isEmpty()) {
      throw new IllegalStateException("OpenAI API returned options for a free-text question.");
    }
    if (!correctLabels.isEmpty()) {
      throw new IllegalStateException("OpenAI API returned correct option labels for a free-text question.");
    }
    if (expectedAnswer.isBlank()) {
      throw new IllegalStateException("OpenAI API returned a free-text question without expectedAnswer.");
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

  private String applyTestTemplate(String subject, String testContent) {
    String safeSubject = escapeHtml(subject == null || subject.isBlank() ? "Generated" : subject);
    return TEST_TEMPLATE.formatted(safeSubject, safeSubject, indentContent(testContent));
  }

  String renderQuestionBank(QuestionBank questionBank) {
    StringBuilder html = new StringBuilder();
    html.append(
        "<label class=\"student-name\">Student name<input type=\"text\" name=\"studentName\" autocomplete=\"name\"></label>\n");
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

    html.append(renderVisual(question.visual()));

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

  private String renderVisual(VisualDto visual) {
    if (visual == null || isBlank(visual.type()) || visual.data() == null || visual.data().isNull()) {
      return "";
    }
    String type = visual.type().trim().toLowerCase(Locale.ROOT);
    if (type.equals("number_line")) {
      return renderNumberLine(visual.data());
    }
    return "";
  }

  private String renderNumberLine(JsonNode data) {
    double min = numberField(data, "min", 0);
    double max = numberField(data, "max", 10);
    double tickStep = numberField(data, "tickStep", 1);
    if (max <= min || tickStep <= 0) {
      return "";
    }

    int tickCount = (int) Math.floor((max - min) / tickStep) + 1;
    if (tickCount < 2 || tickCount > 41) {
      return "";
    }

    int width = 720;
    int height = 170;
    int left = 48;
    int right = width - 48;
    int axisY = 100;
    StringBuilder svg = new StringBuilder();
    svg.append("<div class=\"question-visual\"><svg class=\"number-line\" viewBox=\"0 0 ")
        .append(width)
        .append(" ")
        .append(height)
        .append("\" role=\"img\" aria-label=\"Number line from ")
        .append(escapeAttribute(formatNumber(min)))
        .append(" to ")
        .append(escapeAttribute(formatNumber(max)))
        .append("\">")
        .append("<line x1=\"")
        .append(left)
        .append("\" y1=\"")
        .append(axisY)
        .append("\" x2=\"")
        .append(right)
        .append("\" y2=\"")
        .append(axisY)
        .append("\" stroke=\"#24324d\" stroke-width=\"3\" stroke-linecap=\"round\"/>");

    for (int tick = 0; tick < tickCount; tick++) {
      double value = min + (tick * tickStep);
      double x = numberLineX(value, min, max, left, right);
      svg.append("<line x1=\"")
          .append(formatSvgNumber(x))
          .append("\" y1=\"86\" x2=\"")
          .append(formatSvgNumber(x))
          .append("\" y2=\"114\" stroke=\"#24324d\" stroke-width=\"2\"/>")
          .append("<text x=\"")
          .append(formatSvgNumber(x))
          .append("\" y=\"136\" text-anchor=\"middle\">")
          .append(escapeHtml(formatNumber(value)))
          .append("</text>");
    }

    JsonNode jumps = data.path("jumps");
    if (jumps.isArray()) {
      int jumpIndex = 0;
      for (JsonNode jump : jumps) {
        if (jumpIndex >= 6) {
          break;
        }
        double from = numberField(jump, "from", Double.NaN);
        double to = numberField(jump, "to", Double.NaN);
        if (!Double.isFinite(from) || !Double.isFinite(to) || from < min || from > max || to < min || to > max) {
          continue;
        }
        double x1 = numberLineX(from, min, max, left, right);
        double x2 = numberLineX(to, min, max, left, right);
        double radius = Math.max(18, Math.abs(x2 - x1) / 2);
        double mid = (x1 + x2) / 2;
        double y = axisY - 14 - (jumpIndex * 12);
        String sweep = to >= from ? "1" : "0";
        svg.append("<path d=\"M ")
            .append(formatSvgNumber(x1))
            .append(" ")
            .append(formatSvgNumber(y))
            .append(" A ")
            .append(formatSvgNumber(radius))
            .append(" ")
            .append(formatSvgNumber(radius))
            .append(" 0 0 ")
            .append(sweep)
            .append(" ")
            .append(formatSvgNumber(x2))
            .append(" ")
            .append(formatSvgNumber(y))
            .append("\" fill=\"none\" stroke=\"#1c5f5a\" stroke-width=\"2\"/>");
        String label = textField(jump, "label");
        if (!isBlank(label)) {
          svg.append("<text x=\"")
              .append(formatSvgNumber(mid))
              .append("\" y=\"")
              .append(formatSvgNumber(Math.max(18, y - radius + 18)))
              .append("\" text-anchor=\"middle\" font-weight=\"700\">")
              .append(escapeHtml(label))
              .append("</text>");
        }
        jumpIndex++;
      }
    }

    JsonNode points = data.path("points");
    if (points.isArray()) {
      int pointIndex = 0;
      for (JsonNode point : points) {
        if (pointIndex >= 12) {
          break;
        }
        double value = numberField(point, "value", Double.NaN);
        if (!Double.isFinite(value) || value < min || value > max) {
          continue;
        }
        double x = numberLineX(value, min, max, left, right);
        String label = textField(point, "label");
        svg.append("<circle cx=\"")
            .append(formatSvgNumber(x))
            .append("\" cy=\"")
            .append(axisY)
            .append("\" r=\"7\" fill=\"#1c5f5a\"/>");
        if (!isBlank(label)) {
          svg.append("<text x=\"")
              .append(formatSvgNumber(x))
              .append("\" y=\"72\" text-anchor=\"middle\" font-weight=\"800\">")
              .append(escapeHtml(label))
              .append("</text>");
        }
        pointIndex++;
      }
    }

    svg.append("</svg></div>\n");
    return svg.toString();
  }

  private double numberLineX(double value, double min, double max, int left, int right) {
    return left + ((value - min) / (max - min) * (right - left));
  }

  private double numberField(JsonNode node, String field, double fallback) {
    JsonNode value = node == null ? null : node.get(field);
    return value != null && value.isNumber() ? value.asDouble() : fallback;
  }

  private String textField(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    return value != null && value.isTextual() ? value.asText() : "";
  }

  private String formatNumber(double value) {
    if (Math.rint(value) == value) {
      return String.valueOf((long) value);
    }
    return formatSvgNumber(value);
  }

  private String formatSvgNumber(double value) {
    return String.format(Locale.ROOT, "%.2f", value).replaceAll("\\.?0+$", "");
  }

}
