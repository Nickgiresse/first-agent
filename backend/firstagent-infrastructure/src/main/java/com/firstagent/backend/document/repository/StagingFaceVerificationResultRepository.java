package com.firstagent.backend.document.repository;

import com.firstagent.backend.common.enums.FaceVerificationStatus;
import com.firstagent.backend.document.entity.StagingFaceVerificationResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StagingFaceVerificationResultRepository
    extends JpaRepository<StagingFaceVerificationResult, UUID> {

  Optional<StagingFaceVerificationResult> findByOnboardingSession_Id(UUID onboardingSessionId);

  boolean existsByOnboardingSession_IdAndStatus(
      UUID onboardingSessionId, FaceVerificationStatus status);

  void deleteByOnboardingSession_Id(UUID onboardingSessionId);
}
