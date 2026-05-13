package com.homestudenttester.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import java.time.Instant;

@Entity
public class StoredDocument {
  @Id
  private String id;

  @Lob
  @Column(nullable = false)
  private String rawMarkdown;

  @Lob
  @Column(nullable = false)
  private String parsedJson;

  @Column(nullable = false)
  private Instant createdAt;

  protected StoredDocument() {
  }

  public StoredDocument(String id, String rawMarkdown, String parsedJson, Instant createdAt) {
    this.id = id;
    this.rawMarkdown = rawMarkdown;
    this.parsedJson = parsedJson;
    this.createdAt = createdAt;
  }

  public String getId() {
    return id;
  }

  public String getRawMarkdown() {
    return rawMarkdown;
  }

  public String getParsedJson() {
    return parsedJson;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
