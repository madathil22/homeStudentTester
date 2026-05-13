package com.homestudenttester.service;

import com.homestudenttester.dto.AnswerInfo;
import com.homestudenttester.dto.QuestionBank;
import com.homestudenttester.dto.QuestionDto;
import com.homestudenttester.dto.QuestionScore;
import com.homestudenttester.dto.ScoreResult;
import com.homestudenttester.dto.SubmissionDto;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ScoringService {
  private static final Set<String> OBJECTIVE_TYPES = Set.of("multiple_choice", "multi_select", "text");
  private final MarkdownParserService parserService;

  public ScoringService(MarkdownParserService parserService) {
    this.parserService = parserService;
  }

  public ScoreResult scoreSubmission(
      QuestionBank test,
      Map<String, AnswerInfo> answerBank,
      SubmissionDto submission) {
    Map<String, QuestionScore> byQuestion = new LinkedHashMap<>();
    double earned = 0;
    double possible = 0;

    for (QuestionDto question : test.questions()) {
      String key = question.number();
      AnswerInfo answerInfo = answerBank.get(key);
      double points = answerInfo != null && answerInfo.points() != null ? answerInfo.points() : question.points();
      Object studentAnswer = submission.answers().getOrDefault(key, "");
      Double earnedForQuestion = null;
      String status = "Needs review";

      possible += points;

      if (OBJECTIVE_TYPES.contains(question.type())) {
        boolean correct = isObjectiveCorrect(question.type(), studentAnswer, answerInfo);
        earnedForQuestion = correct ? points : 0;
        status = correct ? "Correct" : "Incorrect";
        earned += earnedForQuestion;
      }

      byQuestion.put(key, new QuestionScore(
          key,
          question.type(),
          points,
          earnedForQuestion,
          status,
          answerInfo == null ? "" : answerInfo.answer(),
          answerInfo == null ? "" : answerInfo.explanation(),
          answerInfo == null ? "" : answerInfo.rubric(),
          answerInfo == null ? "" : answerInfo.sampleAnswer()));
    }

    return new ScoreResult(earned, possible, byQuestion, Instant.now());
  }

  private boolean isObjectiveCorrect(String type, Object studentAnswer, AnswerInfo answerInfo) {
    String expected = answerInfo == null ? "" : answerInfo.answer();

    if (type.equals("multi_select")) {
      return String.join("|", normalizeSet(studentAnswer)).equals(String.join("|", normalizeSet(expected)));
    }

    if (normalizeText(studentAnswer).equals(normalizeText(expected))) {
      return true;
    }

    if (answerInfo == null) {
      return false;
    }

    return answerInfo.accepted().stream()
        .map(this::normalizeText)
        .anyMatch(candidate -> candidate.equals(normalizeText(studentAnswer)));
  }

  private java.util.List<String> normalizeSet(Object value) {
    return parserService.splitAnswers(String.valueOf(value)).stream()
        .map(item -> item.toUpperCase(Locale.ROOT))
        .sorted()
        .toList();
  }

  private String normalizeText(Object value) {
    return String.valueOf(value).trim().toLowerCase(Locale.ROOT);
  }
}
