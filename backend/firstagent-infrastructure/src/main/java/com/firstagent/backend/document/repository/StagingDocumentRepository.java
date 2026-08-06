package com.firstagent.backend.document.repository;

import com.firstagent.backend.common.enums.DocumentType;
import com.firstagent.backend.document.entity.StagingDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StagingDocumentRepository extends JpaRepository<StagingDocument, UUID> {

  List<StagingDocument> findByOnboardingSession_Id(UUID onboardingSessionId);

  Optional<StagingDocument> findByOnboardingSession_IdAndDocumentType(
      UUID onboardingSessionId, DocumentType documentType);

  boolean existsByOnboardingSession_IdAndDocumentType(
      UUID onboardingSessionId, DocumentType documentType);

  void deleteByOnboardingSession_Id(UUID onboardingSessionId);
}
