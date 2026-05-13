package com.homestudenttester.repository;

import com.homestudenttester.model.SubmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<SubmissionEntity, String> {
}
