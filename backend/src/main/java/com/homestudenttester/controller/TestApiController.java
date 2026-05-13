package com.homestudenttester.controller;

import com.homestudenttester.dto.BankDocument;
import com.homestudenttester.dto.GenerateTestRequest;
import com.homestudenttester.dto.QuestionBank;
import com.homestudenttester.dto.SaveMarkdownRequest;
import com.homestudenttester.dto.SaveSubmissionRequest;
import com.homestudenttester.service.AppStateService;
import com.homestudenttester.service.AuthService;
import com.homestudenttester.service.OpenAiService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestApiController {
  private final AppStateService appStateService;
  private final AuthService authService;
  private final OpenAiService openAiService;

  public TestApiController(AppStateService appStateService, AuthService authService, OpenAiService openAiService) {
    this.appStateService = appStateService;
    this.authService = authService;
    this.openAiService = openAiService;
  }

  @GetMapping("/api/test")
  public Map<String, Object> getTest(
      @RequestHeader(value = "x-admin-token", required = false) String adminToken,
      @RequestHeader(value = "x-test-token", required = false) String testToken) {
    authService.requireAny(adminToken, testToken);
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("activeTest", appStateService.activeTest().orElse(null));
    response.put("hasAnswerBank", appStateService.answerBank().isPresent());
    response.put("submissionCount", appStateService.submissions().size());
    response.put("studentLink", authService.studentLink());
    response.put("adminLink", authService.adminLink());
    response.put("resultsLink", authService.resultsLink());
    return response;
  }

  @PostMapping("/api/test")
  public Map<String, Object> saveTest(
      @RequestHeader(value = "x-admin-token", required = false) String adminToken,
      @RequestBody(required = false) SaveMarkdownRequest request) {
    authService.requireAdmin(adminToken);
    String rawMarkdown = request == null || request.rawMarkdown() == null ? "" : request.rawMarkdown();
    BankDocument<QuestionBank> activeTest = appStateService.saveTest(rawMarkdown);
    return Map.of("activeTest", activeTest, "submissionsCleared", true);
  }

  @PostMapping("/api/test/generate")
  public Map<String, Object> generateTest(
      @RequestHeader(value = "x-admin-token", required = false) String adminToken,
      @RequestBody(required = false) GenerateTestRequest request) {
    authService.requireAdmin(adminToken);
    String subject = request == null || request.subject() == null ? "" : request.subject();
    String html = openAiService.generateTestHtml(subject);
    var generatedTest = appStateService.saveGeneratedTestHtml(subject, html);
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

  @PostMapping("/api/answers")
  public Map<String, Object> saveAnswers(
      @RequestHeader(value = "x-admin-token", required = false) String adminToken,
      @RequestBody(required = false) SaveMarkdownRequest request) {
    authService.requireAdmin(adminToken);
    String rawMarkdown = request == null || request.rawMarkdown() == null ? "" : request.rawMarkdown();
    return Map.of("answerBank", appStateService.saveAnswers(rawMarkdown));
  }

  @GetMapping("/api/submissions")
  public Map<String, Object> getSubmissions(
      @RequestHeader(value = "x-admin-token", required = false) String adminToken) {
    authService.requireAdmin(adminToken);
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("submissions", appStateService.submissions());
    response.put("activeTest", appStateService.activeTest().orElse(null));
    response.put("answerBank", appStateService.answerBank().orElse(null));
    return response;
  }

  @PostMapping("/api/submissions")
  public ResponseEntity<Map<String, Object>> saveSubmission(
      @RequestHeader(value = "x-test-token", required = false) String testToken,
      @RequestBody(required = false) SaveSubmissionRequest request) {
    authService.requireTest(testToken);
    SaveSubmissionRequest safeRequest = request == null ? new SaveSubmissionRequest("", Map.of()) : request;
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(Map.of("submission", appStateService.saveSubmission(safeRequest)));
  }

  @DeleteMapping("/api/submissions")
  public Map<String, Object> clearSubmissions(
      @RequestHeader(value = "x-admin-token", required = false) String adminToken) {
    authService.requireAdmin(adminToken);
    appStateService.clearSubmissions();
    return Map.of("ok", true);
  }

  @PostMapping("/api/score")
  public Map<String, Object> scoreAll(
      @RequestHeader(value = "x-admin-token", required = false) String adminToken) {
    authService.requireAdmin(adminToken);
    return Map.of("submissions", appStateService.scoreAll());
  }
}
