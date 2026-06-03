package com.homestudenttester.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import java.time.Instant;

@Entity
public class AnswerKeyCorrection {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String subjectFingerprint;

  @Column(nullable = false)
  private String questionFingerprint;

  @Lob
  @Column(nullable = false)
  private String subject;

  @Column(nullable = false)
  private String questionNumber;

  @Column(nullable = false)
  private String questionType;

  @Lob
  @Column(nullable = false)
  private String prompt;

  @Lob
  @Column(nullable = false)
  private String optionsJson;

  @Lob
  @Column(nullable = false)
  private String correctOptionLabelsJson;

  @Lob
  @Column(nullable = false)
  private String expectedAnswer;

  @Lob
  @Column(nullable = false)
  private String parentNote;

  @Lob
  @Column(nullable = false)
  private String verifierReason;

  @Column(nullable = false)
  private String verifierConfidence;

  @Column(nullable = false)
  private boolean approved;

  @Column(nullable = false)
  private Instant createdAt;

  protected AnswerKeyCorrection() {
  }

  public AnswerKeyCorrection(
      String subjectFingerprint,
      String questionFingerprint,
      String subject,
      String questionNumber,
      String questionType,
      String prompt,
      String optionsJson,
      String correctOptionLabelsJson,
      String expectedAnswer,
      String parentNote,
      String verifierReason,
      String verifierConfidence,
      boolean approved,
      Instant createdAt) {
    this.subjectFingerprint = subjectFingerprint;
    this.questionFingerprint = questionFingerprint;
    this.subject = subject;
    this.questionNumber = questionNumber;
    this.questionType = questionType;
    this.prompt = prompt;
    this.optionsJson = optionsJson;
    this.correctOptionLabelsJson = correctOptionLabelsJson;
    this.expectedAnswer = expectedAnswer;
    this.parentNote = parentNote;
    this.verifierReason = verifierReason;
    this.verifierConfidence = verifierConfidence;
    this.approved = approved;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public String getSubjectFingerprint() {
    return subjectFingerprint;
  }

  public String getQuestionFingerprint() {
    return questionFingerprint;
  }

  public String getSubject() {
    return subject;
  }

  public String getQuestionNumber() {
    return questionNumber;
  }

  public String getQuestionType() {
    return questionType;
  }

  public String getPrompt() {
    return prompt;
  }

  public String getOptionsJson() {
    return optionsJson;
  }

  public String getCorrectOptionLabelsJson() {
    return correctOptionLabelsJson;
  }

  public String getExpectedAnswer() {
    return expectedAnswer;
  }

  public String getParentNote() {
    return parentNote;
  }

  public String getVerifierReason() {
    return verifierReason;
  }

  public String getVerifierConfidence() {
    return verifierConfidence;
  }

  public boolean isApproved() {
    return approved;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
