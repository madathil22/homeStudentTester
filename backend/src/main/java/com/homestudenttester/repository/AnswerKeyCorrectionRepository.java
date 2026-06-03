package com.homestudenttester.repository;

import com.homestudenttester.model.AnswerKeyCorrection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerKeyCorrectionRepository extends JpaRepository<AnswerKeyCorrection, Long> {
  List<AnswerKeyCorrection> findByApprovedTrueOrderByCreatedAtDesc();

  List<AnswerKeyCorrection> findByApprovedTrueAndQuestionFingerprintOrderByCreatedAtDesc(String questionFingerprint);
}
