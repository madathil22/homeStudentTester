package com.homestudenttester.service;

import static com.homestudenttester.utils.ServiceUtils.fingerprint;
import static com.homestudenttester.utils.ServiceUtils.normalizeQuestionType;
import static com.homestudenttester.utils.ServiceUtils.questionFingerprint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestudenttester.dto.AnswerKeyCorrectionInfo;
import com.homestudenttester.dto.AnswerKeyVerification;
import com.homestudenttester.dto.GeneratedTestDocument;
import com.homestudenttester.dto.GeneratedTestInfo;
import com.homestudenttester.dto.GeneratedTestMetadata;
import com.homestudenttester.dto.GeneratedTestResult;
import com.homestudenttester.dto.GeneratedTestSubmission;
import com.homestudenttester.config.AppProperties;
import com.homestudenttester.dto.QuestionBank;
import com.homestudenttester.dto.QuestionDto;
import com.homestudenttester.model.AnswerKeyCorrection;
import com.homestudenttester.model.StoredDocument;
import com.homestudenttester.repository.AnswerKeyCorrectionRepository;
import com.homestudenttester.repository.StoredDocumentRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AppStateService {
  private static final Logger log = LoggerFactory.getLogger(AppStateService.class);
  private static final String GENERATED_TEST_PREFIX = "test";

  private final StoredDocumentRepository documentRepository;
  private final AnswerKeyCorrectionRepository correctionRepository;
  private final ObjectMapper objectMapper;
  private final AppProperties properties;

  public AppStateService(
      StoredDocumentRepository documentRepository,
      AnswerKeyCorrectionRepository correctionRepository,
      ObjectMapper objectMapper,
      AppProperties properties) {
    this.documentRepository = documentRepository;
    this.correctionRepository = correctionRepository;
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  @Transactional
  public GeneratedTestInfo saveGeneratedTest(String subject, GeneratedTestDocument generatedTest) {
    String id = nextGeneratedTestId();
    log.info("Persisting generated test: testId={}, questionCount={}", id, generatedTest.questionBank().questions().size());
    GeneratedTestMetadata metadata = new GeneratedTestMetadata(
        subject,
        generatedTest.questionBank(),
        null,
        null,
        generatedTest.generationUsage());
    StoredDocument document = new StoredDocument(
        id,
        generatedTest.html(),
        writeJson(metadata),
        Instant.now());
    documentRepository.save(document);
    log.info("Generated test persisted: testId={}", id);
    return toGeneratedTestInfo(document);
  }

  public Optional<String> generatedTestHtml(String id) {
    return documentRepository.findById(id)
        .filter(document -> document.getId().startsWith(GENERATED_TEST_PREFIX))
        .map(StoredDocument::getRawMarkdown);
  }

  public GeneratedTestMetadata generatedTestMetadata(String id) {
    return documentRepository.findById(id)
        .filter(document -> document.getId().startsWith(GENERATED_TEST_PREFIX))
        .map(this::readGeneratedTestMetadata)
        .orElseThrow(() -> new IllegalArgumentException("Generated test not found."));
  }

  public List<AnswerKeyCorrection> approvedCorrectionsFor(QuestionBank questionBank) {
    if (questionBank == null || questionBank.questions() == null) {
      return List.of();
    }
    Set<String> fingerprints = new LinkedHashSet<>();
    for (QuestionDto question : questionBank.questions()) {
      fingerprints.add(questionFingerprint(question));
    }
    List<AnswerKeyCorrection> corrections = new ArrayList<>();
    for (String fingerprint : fingerprints) {
      correctionRepository.findByApprovedTrueAndQuestionFingerprintOrderByCreatedAtDesc(fingerprint).stream()
          .findFirst()
          .ifPresent(corrections::add);
    }
    return corrections;
  }

  @Transactional
  public AnswerKeyCorrectionInfo saveAnswerKeyCorrection(
      String subject,
      QuestionDto question,
      AnswerKeyVerification verification,
      String parentNote) {
    String questionType = normalizeQuestionType(question.type());
    AnswerKeyCorrection correction = new AnswerKeyCorrection(
        fingerprint(subject),
        questionFingerprint(question),
        subject == null ? "" : subject,
        questionNumber(question),
        questionType,
        question.prompt() == null ? "" : question.prompt(),
        writeJson(question.options() == null ? List.of() : question.options()),
        writeJson(verification.correctOptionLabels() == null ? List.of() : verification.correctOptionLabels()),
        verification.expectedAnswer() == null ? "" : verification.expectedAnswer().trim(),
        parentNote == null ? "" : parentNote.trim(),
        verification.reason() == null ? "" : verification.reason().trim(),
        verification.confidence() == null ? "" : verification.confidence().trim(),
        verification.approved(),
        Instant.now());
    return toCorrectionInfo(correctionRepository.save(correction));
  }

  public List<AnswerKeyCorrectionInfo> relevantAnswerKeyCorrections(String subject) {
    Set<String> requestedTerms = searchableTerms(subject);
    return correctionRepository.findByApprovedTrueOrderByCreatedAtDesc().stream()
        .filter(correction -> sharesSearchTerm(requestedTerms, correction.getSubject()))
        .limit(8)
        .map(this::toCorrectionInfo)
        .toList();
  }

  @Transactional
  public GeneratedTestInfo saveGeneratedTestResult(
      String id,
      GeneratedTestSubmission submission,
      GeneratedTestResult result) {
    log.info("Persisting generated-test result: testId={}, earned={}, possible={}", id, result.earned(), result.possible());
    StoredDocument existing = documentRepository.findById(id)
        .filter(document -> document.getId().startsWith(GENERATED_TEST_PREFIX))
        .orElseThrow(() -> new IllegalArgumentException("Generated test not found."));
    GeneratedTestMetadata existingMetadata = readGeneratedTestMetadata(existing);
    GeneratedTestMetadata updatedMetadata = new GeneratedTestMetadata(
        existingMetadata.subject(),
        existingMetadata.questionBank(),
        submission,
        result,
        existingMetadata.generationUsage());
    StoredDocument updated = new StoredDocument(
        existing.getId(),
        existing.getRawMarkdown(),
        writeJson(updatedMetadata),
        existing.getCreatedAt());
    GeneratedTestInfo saved = toGeneratedTestInfo(documentRepository.save(updated));
    log.info("Generated-test result persisted: testId={}", id);
    return saved;
  }

  public List<GeneratedTestInfo> generatedTests() {
    return documentRepository.findByIdStartingWith(GENERATED_TEST_PREFIX).stream()
        .map(this::toGeneratedTestInfo)
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
    List<String> colors = sanitizedSlugParts(properties.testLinkColors());
    List<String> animals = sanitizedSlugParts(properties.testLinkAnimals());
    if (colors.isEmpty() || animals.isEmpty()) {
      throw new IllegalStateException("Test link colors and animals must be configured.");
    }

    List<StoredDocument> existingTests = documentRepository.findByIdStartingWith(GENERATED_TEST_PREFIX);
    long index = existingTests.size();
    int combinations = colors.size() * animals.size();
    for (long attempt = 0; attempt < combinations; attempt++) {
      long slot = index + attempt;
      String baseId = buildReadableTestId(colors, animals, slot);
      if (!documentRepository.existsById(baseId)) {
        return baseId;
      }
    }

    String baseId = buildReadableTestId(colors, animals, index);
    int suffix = 2;
    while (documentRepository.existsById(baseId + "-" + suffix)) {
      suffix++;
    }
    return baseId + "-" + suffix;
  }

  private String buildReadableTestId(List<String> colors, List<String> animals, long index) {
    String color = colors.get((int) (index % colors.size()));
    String animal = animals.get((int) ((index / colors.size()) % animals.size()));
    return GENERATED_TEST_PREFIX + "-" + color + "-" + animal;
  }

  private List<String> sanitizedSlugParts(List<String> values) {
    if (values == null) {
      return List.of();
    }
    return values.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(value -> value.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-"))
        .map(value -> value.replaceAll("^-+|-+$", ""))
        .filter(value -> !value.isBlank())
        .distinct()
        .toList();
  }

  private GeneratedTestInfo toGeneratedTestInfo(StoredDocument document) {
    GeneratedTestMetadata metadata = readGeneratedTestMetadata(document);
    String subject = metadata.subject() == null || metadata.subject().isBlank()
        ? "Unknown subject"
        : metadata.subject();
    return new GeneratedTestInfo(
        document.getId(),
        subject,
        "/" + document.getId(),
        document.getCreatedAt().toString(),
        metadata.result(),
        metadata.generationUsage());
  }

  private GeneratedTestMetadata readGeneratedTestMetadata(StoredDocument document) {
    try {
      GeneratedTestMetadata metadata = objectMapper.readValue(document.getParsedJson(), GeneratedTestMetadata.class);
      if (metadata.subject() != null || metadata.questionBank() != null || metadata.result() != null
          || metadata.generationUsage() != null) {
        return metadata;
      }
    } catch (JsonProcessingException ignored) {
      // Fall through to the legacy metadata shape used before generated tests stored question-bank JSON.
    }

    Map<String, String> legacyMetadata = readJson(document.getParsedJson(), new TypeReference<Map<String, String>>() {
    });
    return new GeneratedTestMetadata(
        legacyMetadata.getOrDefault("subject", "Unknown subject"),
        null,
        null,
        null,
        null);
  }

  public AnswerKeyCorrectionInfo toCorrectionInfo(AnswerKeyCorrection correction) {
    return new AnswerKeyCorrectionInfo(
        correction.getId(),
        correction.getSubject(),
        correction.getQuestionNumber(),
        correction.getQuestionType(),
        correction.getPrompt(),
        readJson(correction.getCorrectOptionLabelsJson(), new TypeReference<List<String>>() {
        }),
        correction.getExpectedAnswer(),
        correction.getVerifierReason(),
        correction.getVerifierConfidence(),
        correction.getCreatedAt());
  }

  private String questionNumber(QuestionDto question) {
    return question == null || question.number() == null || question.number().isBlank()
        ? ""
        : question.number().trim();
  }

  private Set<String> searchableTerms(String value) {
    Set<String> terms = new LinkedHashSet<>();
    if (value == null) {
      return terms;
    }
    for (String term : value.toLowerCase().split("[^a-z0-9]+")) {
      if (term.length() > 2) {
        terms.add(term);
      }
    }
    return terms;
  }

  private boolean sharesSearchTerm(Set<String> requestedTerms, String subject) {
    if (requestedTerms.isEmpty()) {
      return true;
    }
    Set<String> correctionTerms = searchableTerms(subject);
    for (String term : requestedTerms) {
      if (correctionTerms.contains(term)) {
        return true;
      }
    }
    return false;
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
