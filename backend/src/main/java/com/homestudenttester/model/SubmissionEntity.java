package com.homestudenttester.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import java.time.Instant;

@Entity
public class SubmissionEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String studentName;

  @Column(nullable = false)
  private Instant submittedAt;

  @Lob
  @Column(nullable = false)
  private String answersJson;

  @Lob
  private String scoreJson;

  protected SubmissionEntity() {
  }

  public SubmissionEntity(
      String id,
      String studentName,
      Instant submittedAt,
      String answersJson,
      String scoreJson) {
    this.id = id;
    this.studentName = studentName;
    this.submittedAt = submittedAt;
    this.answersJson = answersJson;
    this.scoreJson = scoreJson;
  }

  public String getId() {
    return id;
  }

  public String getStudentName() {
    return studentName;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public String getAnswersJson() {
    return answersJson;
  }

  public String getScoreJson() {
    return scoreJson;
  }

  public void setScoreJson(String scoreJson) {
    this.scoreJson = scoreJson;
  }
}
