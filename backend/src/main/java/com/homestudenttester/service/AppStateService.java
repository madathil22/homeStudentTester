package com.homestudenttester.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestudenttester.dto.AnswerInfo;
import com.homestudenttester.dto.BankDocument;
import com.homestudenttester.dto.GeneratedTestInfo;
import com.homestudenttester.dto.QuestionBank;
import com.homestudenttester.dto.SaveSubmissionRequest;
import com.homestudenttester.dto.ScoreResult;
import com.homestudenttester.dto.SubmissionDto;
import com.homestudenttester.model.StoredDocument;
import com.homestudenttester.model.SubmissionEntity;
import com.homestudenttester.repository.StoredDocumentRepository;
import com.homestudenttester.repository.SubmissionRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppStateService {
  private static final String ACTIVE_TEST_ID = "active-test";
  private static final String ANSWER_BANK_ID = "answer-bank";
  private static final String GENERATED_TEST_PREFIX = "test";

  private final StoredDocumentRepository documentRepository;
  private final SubmissionRepository submissionRepository;
  private final MarkdownParserService parserService;
  private final ScoringService scoringService;
  private final ObjectMapper objectMapper;

  public AppStateService(
      StoredDocumentRepository documentRepository,
      SubmissionRepository submissionRepository,
      MarkdownParserService parserService,
      ScoringService scoringService,
      ObjectMapper objectMapper) {
    this.documentRepository = documentRepository;
    this.submissionRepository = submissionRepository;
    this.parserService = parserService;
    this.scoringService = scoringService;
    this.objectMapper = objectMapper;
  }

  public Optional<BankDocument<QuestionBank>> activeTest() {
    return documentRepository.findById(ACTIVE_TEST_ID)
        .map(document -> new BankDocument<>(
            document.getRawMarkdown(),
            readJson(document.getParsedJson(), QuestionBank.class),
            document.getCreatedAt()));
  }

  public Optional<BankDocument<Map<String, AnswerInfo>>> answerBank() {
    return documentRepository.findById(ANSWER_BANK_ID)
        .map(document -> new BankDocument<>(
            document.getRawMarkdown(),
            readJson(document.getParsedJson(), new TypeReference<Map<String, AnswerInfo>>() {
            }),
            document.getCreatedAt()));
  }

  @Transactional
  public BankDocument<QuestionBank> saveTest(String rawMarkdown) {
    QuestionBank parsed = parserService.parseQuestionBank(rawMarkdown);
    StoredDocument document = new StoredDocument(
        ACTIVE_TEST_ID,
        rawMarkdown,
        writeJson(parsed),
        Instant.now());
    documentRepository.save(document);
    submissionRepository.deleteAll();
    return new BankDocument<>(document.getRawMarkdown(), parsed, document.getCreatedAt());
  }

  @Transactional
  public BankDocument<Map<String, AnswerInfo>> saveAnswers(String rawMarkdown) {
    Map<String, AnswerInfo> parsed = parserService.parseAnswerBank(rawMarkdown);
    StoredDocument document = new StoredDocument(
        ANSWER_BANK_ID,
        rawMarkdown,
        writeJson(parsed),
        Instant.now());
    documentRepository.save(document);
    return new BankDocument<>(document.getRawMarkdown(), parsed, document.getCreatedAt());
  }

  @Transactional
  public GeneratedTestInfo saveGeneratedTestHtml(String subject, String html) {
    String id = nextGeneratedTestId();
    StoredDocument document = new StoredDocument(
        id,
        html,
        writeJson(Map.of("subject", subject)),
        Instant.now());
    documentRepository.save(document);
    return new GeneratedTestInfo(id, subject, "/" + id, document.getCreatedAt().toString());
  }

  public Optional<String> generatedTestHtml(String id) {
    return documentRepository.findById(id)
        .filter(document -> document.getId().startsWith(GENERATED_TEST_PREFIX))
        .map(StoredDocument::getRawMarkdown);
  }

  public List<GeneratedTestInfo> generatedTests() {
    return documentRepository.findByIdStartingWith(GENERATED_TEST_PREFIX).stream()
        .map(document -> {
          Map<String, String> metadata = readJson(document.getParsedJson(), new TypeReference<Map<String, String>>() {
          });
          String subject = metadata.getOrDefault("subject", "Unknown subject");
          return new GeneratedTestInfo(
              document.getId(),
              subject,
              "/" + document.getId(),
              document.getCreatedAt().toString());
        })
        .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
        .toList();
  }

  @Transactional
  public void deleteGeneratedTest(String id) {
    if (!id.startsWith(GENERATED_TEST_PREFIX)) {
      throw new IllegalArgumentException("Invalid generated test id.");
    }
    if (!documentRepository.existsById(id)) {
      throw new IllegalArgumentException("Generated test not found.");
    }
    documentRepository.deleteById(id);
  }

  private String nextGeneratedTestId() {
    long number = documentRepository.findByIdStartingWith(GENERATED_TEST_PREFIX).stream()
        .mapToLong(document -> {
          try {
            return Long.parseLong(document.getId().substring(GENERATED_TEST_PREFIX.length()));
          } catch (NumberFormatException ex) {
            return 0L;
          }
        })
        .max()
        .orElse(0L);
    return GENERATED_TEST_PREFIX + (number + 1);
  }

  public List<SubmissionDto> submissions() {
    return submissionRepository.findAll().stream()
        .sorted(Comparator.comparing(SubmissionEntity::getSubmittedAt))
        .map(this::toDto)
        .toList();
  }

  @Transactional
  public SubmissionDto saveSubmission(SaveSubmissionRequest request) {
    if (activeTest().isEmpty()) {
      throw new IllegalStateException("No active test is available.");
    }

    String studentName = request.studentName() == null ? "" : request.studentName().trim();
    if (studentName.isBlank()) {
      throw new IllegalArgumentException("Student name is required.");
    }

    SubmissionEntity submission = new SubmissionEntity(
        UUID.randomUUID().toString(),
        studentName,
        Instant.now(),
        writeJson(request.answers() == null ? Map.of() : request.answers()),
        null);
    return toDto(submissionRepository.save(submission));
  }

  @Transactional
  public void clearSubmissions() {
    submissionRepository.deleteAll();
  }

  @Transactional
  public List<SubmissionDto> scoreAll() {
    QuestionBank test = activeTest()
        .orElseThrow(() -> new IllegalStateException("No active test to score."))
        .parsed();
    Map<String, AnswerInfo> answers = answerBank()
        .orElseThrow(() -> new IllegalStateException("No answer bank to score against."))
        .parsed();

    List<SubmissionEntity> submissions = submissionRepository.findAll();
    submissions.forEach(submission -> {
      ScoreResult score = scoringService.scoreSubmission(test, answers, toDto(submission));
      submission.setScoreJson(writeJson(score));
    });
    return submissionRepository.saveAll(submissions).stream()
        .sorted(Comparator.comparing(SubmissionEntity::getSubmittedAt))
        .map(this::toDto)
        .toList();
  }

  private SubmissionDto toDto(SubmissionEntity entity) {
    return new SubmissionDto(
        entity.getId(),
        entity.getStudentName(),
        entity.getSubmittedAt(),
        readJson(entity.getAnswersJson(), new TypeReference<Map<String, Object>>() {
        }),
        entity.getScoreJson() == null ? null : readJson(entity.getScoreJson(), ScoreResult.class));
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("Unable to store JSON.", error);
    }
  }

  private <T> T readJson(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("Unable to read stored JSON.", error);
    }
  }

  private <T> T readJson(String json, TypeReference<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("Unable to read stored JSON.", error);
    }
  }
}
