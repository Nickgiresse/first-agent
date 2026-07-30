package com.firstagent.backend.document.repository;

import com.firstagent.backend.common.enums.OcrStatus;
import com.firstagent.backend.document.entity.StagingOcrResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StagingOcrResultRepository extends JpaRepository<StagingOcrResult, UUID> {

    Optional<StagingOcrResult> findByOnboardingSession_Id(UUID onboardingSessionId);

    boolean existsByOnboardingSession_IdAndStatus(UUID onboardingSessionId, OcrStatus status);

    void deleteByOnboardingSession_Id(UUID onboardingSessionId);
}
