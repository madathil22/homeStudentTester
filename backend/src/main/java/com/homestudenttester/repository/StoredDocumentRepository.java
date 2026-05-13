package com.homestudenttester.repository;

import com.homestudenttester.model.StoredDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredDocumentRepository extends JpaRepository<StoredDocument, String> {
}
