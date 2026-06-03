package com.homestudenttester.service;

import com.homestudenttester.dto.AnswerKeyCorrectionInfo;
import com.homestudenttester.dto.AnswerKeyCorrectionRequest;
import com.homestudenttester.dto.AnswerKeyVerification;
import com.homestudenttester.dto.GeneratedTestDocument;
import com.homestudenttester.dto.GeneratedTestResult;
import com.homestudenttester.dto.GeneratedTestSubmission;
import com.homestudenttester.dto.QuestionBank;
import com.homestudenttester.dto.QuestionDto;
import com.homestudenttester.model.AnswerKeyCorrection;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OpenAiService {
  private final OpenAiGeneratorService generatorService;
  private final OpenAiScorerService scorerService;
  private final OpenAiFeedbackService feedbackService;

  public OpenAiService(
      OpenAiGeneratorService generatorService,
      OpenAiScorerService scorerService,
      OpenAiFeedbackService feedbackService) {
    this.generatorService = generatorService;
    this.scorerService = scorerService;
    this.feedbackService = feedbackService;
  }

  public GeneratedTestDocument generateTest(String subject) {
    return generateTest(subject, List.of());
  }

  public GeneratedTestDocument generateTest(String subject, List<AnswerKeyCorrectionInfo> correctionMemories) {
    return generatorService.generateTest(subject, correctionMemories);
  }

  public GeneratedTestResult scoreGeneratedTest(QuestionBank questionBank, GeneratedTestSubmission submission) {
    return scorerService.scoreGeneratedTest(questionBank, submission);
  }

  public GeneratedTestResult scoreGeneratedTest(
      QuestionBank questionBank,
      GeneratedTestSubmission submission,
      List<AnswerKeyCorrection> corrections) {
    return scorerService.scoreGeneratedTest(questionBank, submission, corrections);
  }

  public AnswerKeyVerification verifyAnswerKeyCorrection(
      String subject,
      QuestionDto question,
      AnswerKeyCorrectionRequest correctionRequest) {
    return feedbackService.verifyAnswerKeyCorrection(subject, question, correctionRequest);
  }
}
