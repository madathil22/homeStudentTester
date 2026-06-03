package com.homestudenttester.controller;

import com.homestudenttester.dto.GenerateTestRequest;
import com.homestudenttester.dto.AnswerKeyCorrectionRequest;
import com.homestudenttester.dto.GeneratedTestSubmission;
import com.homestudenttester.dto.GeneratedTestSubmissionRequest;
import com.homestudenttester.dto.QuestionDto;
import com.homestudenttester.service.AppStateService;
import com.homestudenttester.service.AuthService;
import com.homestudenttester.service.OpenAiService;
import java.util.Map;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestApiController {
  private static final Logger log = LoggerFactory.getLogger(TestApiController.class);

  private final AppStateService appStateService;
  private final AuthService authService;
  private final OpenAiService openAiService;

  public TestApiController(AppStateService appStateService, AuthService authService, OpenAiService openAiService) {
    this.appStateService = appStateService;
    this.authService = authService;
    this.openAiService = openAiService;
  }

  @PostMapping("/api/test/generate")
  public Map<String, Object> generateTest(
      @RequestHeader(value = "x-admin-token", required = false) String adminToken,
      @RequestBody(required = false) GenerateTestRequest request) {
    authService.requireAdmin(adminToken);
    String subject = request == null || request.subject() == null ? "" : request.subject();
    log.info("Received generated-test request: {}", subject);
    var generatedDocument = openAiService.generateTest(subject, appStateService.relevantAnswerKeyCorrections(subject));
    var generatedTest = appStateService.saveGeneratedTest(subject, generatedDocument);
    log.info("Generated test published: testId={}, link={}", generatedTest.id(), generatedTest.link());
    return Map.of(
        "testLink", generatedTest.link(),
        "testId", generatedTest.id(),
        "subject", generatedTest.subject(),
        "createdAt", generatedTest.createdAt());
  }

  @GetMapping("/api/tests")
  public Map<String, Object> getGeneratedTests(
      @RequestHeader(value = "x-admin-token", required = false) String adminToken) {
    authService.requireAdmin(adminToken);
    return Map.of("tests", appStateService.generatedTests());
  }

  @DeleteMapping("/api/tests/{testId}")
  public Map<String, Object> deleteGeneratedTest(
      @RequestHeader(value = "x-admin-token", required = false) String adminToken,
      @PathVariable String testId) {
    authService.requireAdmin(adminToken);
    appStateService.deleteGeneratedTest(testId);
    return Map.of("ok", true);
  }

  @GetMapping(value = "/api/test/html/{testId}", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> getGeneratedTestHtml(@PathVariable String testId) {
    return appStateService.generatedTestHtml(testId)
        .map(html -> ResponseEntity.ok().body(html))
        .orElse(ResponseEntity.notFound().build());
  }

  @PostMapping("/api/test/{testId}/submit")
  public Map<String, Object> submitGeneratedTest(
      @PathVariable String testId,
      @RequestBody(required = false) GeneratedTestSubmissionRequest request) {
    log.info("Received generated-test submission: testId={}", testId);
    var metadata = appStateService.generatedTestMetadata(testId);
    if (metadata.questionBank() == null) {
      throw new IllegalStateException("Generated test does not have question-bank JSON available for scoring.");
    }
    GeneratedTestSubmissionRequest safeRequest = request == null
        ? new GeneratedTestSubmissionRequest("", Map.of(), 0)
        : request;
    String studentName = safeRequest.studentName() == null ? "" : safeRequest.studentName().trim();
    if (studentName.isBlank()) {
      throw new IllegalArgumentException("Student name is required.");
    }
    GeneratedTestSubmission submission = new GeneratedTestSubmission(
        studentName,
        safeRequest.answers() == null ? Map.of() : safeRequest.answers(),
        Math.max(0, safeRequest.elapsedSeconds()),
        Instant.now());
    log.info(
        "Scoring generated-test submission: testId={}, studentNamePresent={}, answerCount={}, elapsedSeconds={}",
        testId,
        !studentName.isBlank(),
        submission.answers().size(),
        submission.elapsedSeconds());
    var result = openAiService.scoreGeneratedTest(
        metadata.questionBank(),
        submission,
        appStateService.approvedCorrectionsFor(metadata.questionBank()));
    var generatedTest = appStateService.saveGeneratedTestResult(testId, submission, result);
    log.info(
        "Generated-test submission scored: testId={}, earned={}, possible={}",
        testId,
        result.earned(),
        result.possible());
    return Map.of(
        "test", generatedTest,
        "result", result);
  }

  @PostMapping("/api/test/{testId}/questions/{questionNumber}/correction")
  public Map<String, Object> correctAnswerKey(
      @RequestHeader(value = "x-admin-token", required = false) String adminToken,
      @PathVariable String testId,
      @PathVariable String questionNumber,
      @RequestBody(required = false) AnswerKeyCorrectionRequest request) {
    authService.requireAdmin(adminToken);
    log.info("Received answer-key correction request: testId={}, questionNumber={}", testId, questionNumber);
    var metadata = appStateService.generatedTestMetadata(testId);
    if (metadata.questionBank() == null) {
      throw new IllegalStateException("Generated test does not have question-bank JSON available for correction.");
    }
    QuestionDto question = findQuestion(metadata.questionBank().questions(), questionNumber);
    AnswerKeyCorrectionRequest safeRequest = request == null
        ? new AnswerKeyCorrectionRequest(questionNumber, java.util.List.of(), "", "")
        : new AnswerKeyCorrectionRequest(
            questionNumber,
            request.correctOptionLabels() == null ? java.util.List.of() : request.correctOptionLabels(),
            request.expectedAnswer() == null ? "" : request.expectedAnswer(),
            request.parentNote() == null ? "" : request.parentNote());
    var verification = openAiService.verifyAnswerKeyCorrection(metadata.subject(), question, safeRequest);
    var correction = appStateService.saveAnswerKeyCorrection(
        metadata.subject(),
        question,
        verification,
        safeRequest.parentNote());
    Object result = metadata.result();
    Object generatedTest = null;
    if (metadata.submission() != null) {
      var rescored = openAiService.scoreGeneratedTest(
          metadata.questionBank(),
          metadata.submission(),
          appStateService.approvedCorrectionsFor(metadata.questionBank()));
      generatedTest = appStateService.saveGeneratedTestResult(testId, metadata.submission(), rescored);
      result = rescored;
    }
    return Map.of(
        "correction", correction,
        "test", generatedTest == null ? appStateService.generatedTests().stream()
            .filter(test -> test.id().equals(testId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Generated test not found.")) : generatedTest,
        "result", result == null ? Map.of() : result);
  }

  private QuestionDto findQuestion(java.util.List<QuestionDto> questions, String questionNumber) {
    if (questions == null || questions.isEmpty()) {
      throw new IllegalArgumentException("No questions are available for correction.");
    }
    String requested = questionNumber == null ? "" : questionNumber.trim();
    for (int index = 0; index < questions.size(); index++) {
      QuestionDto question = questions.get(index);
      String number = question.number() == null || question.number().isBlank()
          ? String.valueOf(index + 1)
          : question.number().trim();
      if (number.equals(requested)) {
        return question;
      }
    }
    throw new IllegalArgumentException("Question not found.");
  }

}
