package com.homestudenttester.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestudenttester.dto.GeneratedTestDocument;
import com.homestudenttester.dto.GeneratedTestInfo;
import com.homestudenttester.dto.GeneratedTestMetadata;
import com.homestudenttester.dto.GeneratedTestResult;
import com.homestudenttester.dto.GeneratedTestSubmission;
import com.homestudenttester.model.StoredDocument;
import com.homestudenttester.repository.StoredDocumentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AppStateService {
  private static final Logger log = LoggerFactory.getLogger(AppStateService.class);
  private static final String GENERATED_TEST_PREFIX = "test";

  private final StoredDocumentRepository documentRepository;
  private final ObjectMapper objectMapper;

  public AppStateService(
      StoredDocumentRepository documentRepository,
      ObjectMapper objectMapper) {
    this.documentRepository = documentRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public GeneratedTestInfo saveGeneratedTest(String subject, GeneratedTestDocument generatedTest) {
    String id = nextGeneratedTestId();
    log.info("Persisting generated test: testId={}, questionCount={}", id, generatedTest.questionBank().questions().size());
    GeneratedTestMetadata metadata = new GeneratedTestMetadata(
        subject,
        generatedTest.questionBank(),
        null,
        null);
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
        result);
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
        metadata.result());
  }

  private GeneratedTestMetadata readGeneratedTestMetadata(StoredDocument document) {
    try {
      GeneratedTestMetadata metadata = objectMapper.readValue(document.getParsedJson(), GeneratedTestMetadata.class);
      if (metadata.subject() != null || metadata.questionBank() != null || metadata.result() != null) {
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
        null);
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
