package com.firstagent.backend.liveness.repository;

import com.firstagent.backend.common.enums.LivenessStatus;
import com.firstagent.backend.liveness.entity.StagingLivenessResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StagingLivenessResultRepository
    extends JpaRepository<StagingLivenessResult, UUID> {

  Optional<StagingLivenessResult> findByOnboardingSession_Id(UUID onboardingSessionId);

  boolean existsByOnboardingSession_IdAndStatus(UUID onboardingSessionId, LivenessStatus status);

  void deleteByOnboardingSession_Id(UUID onboardingSessionId);
}
