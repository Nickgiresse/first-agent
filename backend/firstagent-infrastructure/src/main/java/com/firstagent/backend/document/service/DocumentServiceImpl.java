package com.firstagent.backend.document.service;

import com.firstagent.backend.common.enums.DocumentType;
import com.firstagent.backend.common.exception.BusinessException;
import com.firstagent.backend.document.dto.DocumentUploadResponse;
import com.firstagent.backend.document.entity.StagingDocument;
import com.firstagent.backend.document.repository.StagingDocumentRepository;
import com.firstagent.backend.onboarding.entity.OnboardingSession;
import com.firstagent.backend.onboarding.service.OnboardingSessionService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

  private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png");
  private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
  private static final Set<DocumentType> REQUIRED_TYPES =
      Set.of(DocumentType.CNI_RECTO, DocumentType.CNI_VERSO, DocumentType.SELFIE);

  private final StagingDocumentRepository stagingDocumentRepository;
  private final OnboardingSessionService onboardingSessionService;
  private final StorageService storageService;

  @Override
  @Transactional
  public DocumentUploadResponse uploadDocument(
      String sessionToken, DocumentType documentType, MultipartFile file) {
    OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);

    validateFile(file);

    Optional<StagingDocument> existing =
        stagingDocumentRepository.findByOnboardingSession_IdAndDocumentType(
            session.getId(), documentType);

    String filePath = storageService.store(file, session.getId().toString());

    StagingDocument document =
        existing.orElseGet(
            () ->
                StagingDocument.builder()
                    .onboardingSession(session)
                    .documentType(documentType)
                    .build());
    existing.ifPresent(previous -> storageService.delete(previous.getFilePath()));

    document.setFilePath(filePath);
    document.setFileName(file.getOriginalFilename());
    document.setMimeType(file.getContentType());
    document.setFileSize(file.getSize());

    stagingDocumentRepository.save(document);

    return DocumentUploadResponse.builder()
        .documentId(document.getId())
        .documentType(document.getDocumentType().name())
        .fileName(document.getFileName())
        .uploadedAt(document.getUploadedAt())
        .build();
  }

  @Override
  public boolean hasAllRequiredDocuments(String sessionToken) {
    OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);
    List<StagingDocument> documents =
        stagingDocumentRepository.findByOnboardingSession_Id(session.getId());
    Set<DocumentType> uploadedTypes =
        documents.stream().map(StagingDocument::getDocumentType).collect(Collectors.toSet());

    return uploadedTypes.containsAll(REQUIRED_TYPES);
  }

  private void validateFile(MultipartFile file) {
    if (file.isEmpty()) {
      throw new BusinessException("Le fichier est vide");
    }
    if (!ALLOWED_MIME_TYPES.contains(file.getContentType())) {
      throw new BusinessException("Seuls les fichiers JPEG et PNG sont acceptés");
    }
    if (file.getSize() > MAX_FILE_SIZE) {
      throw new BusinessException("Le fichier ne doit pas dépasser 5 Mo");
    }
  }
}
