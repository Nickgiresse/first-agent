package com.firstagent.backend.document.service;

import com.firstagent.backend.common.enums.DocumentType;
import com.firstagent.backend.common.enums.OcrStatus;
import com.firstagent.backend.common.exception.BusinessException;
import com.firstagent.backend.common.exception.ResourceNotFoundException;
import com.firstagent.backend.common.exception.TypeErreurMetier;
import com.firstagent.backend.common.util.StringSimilarity;
import com.firstagent.backend.document.dto.OcrConfirmationRequest;
import com.firstagent.backend.document.dto.OcrExtractionResponse;
import com.firstagent.backend.document.entity.StagingDocument;
import com.firstagent.backend.document.entity.StagingOcrResult;
import com.firstagent.backend.document.model.OcrExtractionResult;
import com.firstagent.backend.document.port.OcrProvider;
import com.firstagent.backend.document.repository.StagingDocumentRepository;
import com.firstagent.backend.document.repository.StagingOcrResultRepository;
import com.firstagent.backend.onboarding.entity.OnboardingSession;
import com.firstagent.backend.onboarding.service.OnboardingSessionService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OcrServiceImpl implements OcrService {

  private static final String NOT_EXTRACTED_MARKER = "Non extrait";
  private static final String CNI_KIND = "CNI";
  private static final String RECEPISSE_KIND = "RECEPISSE";
  private static final double DOCUMENT_NUMBER_SIMILARITY_THRESHOLD = 0.85;

  @Value("${app.ocr.min-confidence-score:60}")
  private double minConfidenceScore;

  @Value("${app.ocr.name-similarity-threshold:0.75}")
  private double ocrNameSimilarityThreshold;

  @Value("${app.identity.name-similarity-threshold:0.70}")
  private double identityNameSimilarityThreshold;

  private final StagingDocumentRepository stagingDocumentRepository;
  private final StagingOcrResultRepository stagingOcrResultRepository;
  private final OnboardingSessionService onboardingSessionService;
  private final StorageService storageService;
  private final OcrProvider ocrProvider;

  @Override
  @Transactional(noRollbackFor = BusinessException.class)
  public OcrExtractionResponse extractDocumentData(String sessionToken) {
    OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);

    StagingDocument front =
        stagingDocumentRepository
            .findByOnboardingSession_IdAndDocumentType(session.getId(), DocumentType.CNI_RECTO)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "Le recto de la CNI doit être téléversé avant l'extraction"));
    StagingDocument back =
        stagingDocumentRepository
            .findByOnboardingSession_IdAndDocumentType(session.getId(), DocumentType.CNI_VERSO)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "Le verso de la CNI doit être téléversé avant l'extraction"));

    byte[] frontBytes = storageService.read(front.getFilePath());
    byte[] backBytes = storageService.read(back.getFilePath());

    OcrExtractionResult result = ocrProvider.extractIdentityDocument(frontBytes, backBytes);

    StagingOcrResult ocrResult =
        stagingOcrResultRepository
            .findByOnboardingSession_Id(session.getId())
            .orElseGet(() -> StagingOcrResult.builder().onboardingSession(session).build());

    ocrResult.setDocumentKind(result.documentKind());
    ocrResult.setFirstName(result.firstName());
    ocrResult.setLastName(result.lastName());
    ocrResult.setDocumentNumber(result.documentNumber());
    ocrResult.setSex(result.sex());
    ocrResult.setBirthDate(result.birthDate());
    ocrResult.setExpiryDate(result.expiryDate());
    ocrResult.setBirthPlace(result.birthPlace());
    ocrResult.setFatherName(result.fatherName());
    ocrResult.setMotherName(result.motherName());
    ocrResult.setKitNumber(result.kitNumber());
    ocrResult.setRequestIdentifier(result.requestIdentifier());
    ocrResult.setPaymentAmount(result.paymentAmount());
    ocrResult.setPaymentDate(result.paymentDate());
    ocrResult.setConfidenceScore(result.confidenceScore());
    ocrResult.setDocumentQualityScore(result.documentQualityScore());
    ocrResult.setOcrProvider(ocrProvider.getProviderName());
    ocrResult.setExtractedAt(LocalDateTime.now());
    ocrResult.setConfirmedAt(null);

    boolean hasIssues = result.issues() != null && !result.issues().isEmpty();
    boolean lowConfidence = result.confidenceScore() < minConfidenceScore;
    ocrResult.setStatus((lowConfidence || hasIssues) ? OcrStatus.FAILED : OcrStatus.EXTRACTED);

    OcrExtractionResponse response = mapToResponse(stagingOcrResultRepository.save(ocrResult));

    if (hasIssues) {
      throw new BusinessException(
          "Le document a été refusé pour le(s) motif(s) suivant(s) : "
              + String.join(" ; ", result.issues()));
    }

    if (lowConfidence) {
      throw new BusinessException(
          "La qualité de l'extraction est trop faible (confiance "
              + Math.round(result.confidenceScore())
              + "%). Reprenez des photos nettes et bien éclairées du recto et du verso de votre CNI.");
    }

    return response;
  }

  @Override
  public OcrExtractionResponse getExtractedData(String sessionToken) {
    OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);
    return mapToResponse(findExistingResult(session.getId()));
  }

  @Override
  @Transactional
  public OcrExtractionResponse confirmExtractedData(
      String sessionToken, OcrConfirmationRequest request) {
    OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);
    StagingOcrResult ocrResult = findExistingResult(session.getId());

    if (ocrResult.getStatus() != OcrStatus.EXTRACTED
        && ocrResult.getStatus() != OcrStatus.CONFIRMED) {
      throw new BusinessException(
          "Aucune extraction OCR valide à confirmer pour ce client. Relancez l'extraction.");
    }

    boolean isCni = CNI_KIND.equals(ocrResult.getDocumentKind());
    boolean isReceipt = RECEPISSE_KIND.equals(ocrResult.getDocumentKind());

    // Un récépissé de paiement n'a ni date de naissance ni date d'expiration (seulement une
    // date de paiement) : ces vérifications ne s'appliquent qu'à la CNI/au titre provisoire.
    if (isReceipt) {
      if (request.getPaymentDate() == null) {
        throw new BusinessException("La date de paiement est obligatoire");
      }
    } else {
      if (request.getBirthDate() == null) {
        throw new BusinessException("La date de naissance est obligatoire");
      }
      if (request.getExpiryDate() == null) {
        throw new BusinessException("La date d'expiration est obligatoire");
      }
      if (request.getExpiryDate().isBefore(LocalDate.now())) {
        throw new BusinessException(
            "Le document est expiré, il ne peut pas être utilisé pour la vérification d'identité");
      }
      ensureBirthDateMatchesExtraction(ocrResult.getBirthDate(), request.getBirthDate());
    }

    ensureNameMatchesExtraction("prénom", ocrResult.getFirstName(), request.getFirstName());
    ensureNameMatchesExtraction("nom", ocrResult.getLastName(), request.getLastName());
    if (isCni) {
      ensureDocumentNumberMatchesExtraction(
          ocrResult.getDocumentNumber(), request.getDocumentNumber());
    }
    ensureMatchesAccountHolder(
        session.getBankAccount().getFirstName(), session.getBankAccount().getLastName(), request);

    ocrResult.setFirstName(request.getFirstName());
    ocrResult.setLastName(request.getLastName());
    if (isCni) {
      ocrResult.setBirthDate(request.getBirthDate());
      ocrResult.setExpiryDate(request.getExpiryDate());
      ocrResult.setDocumentNumber(request.getDocumentNumber());
      ocrResult.setSex(request.getSex());
    } else if (isReceipt) {
      ocrResult.setKitNumber(request.getKitNumber());
      ocrResult.setRequestIdentifier(request.getRequestIdentifier());
      ocrResult.setPaymentAmount(request.getPaymentAmount());
      ocrResult.setPaymentDate(request.getPaymentDate());
    } else {
      ocrResult.setBirthDate(request.getBirthDate());
      ocrResult.setExpiryDate(request.getExpiryDate());
      ocrResult.setBirthPlace(request.getBirthPlace());
      ocrResult.setFatherName(request.getFatherName());
      ocrResult.setMotherName(request.getMotherName());
      ocrResult.setKitNumber(request.getKitNumber());
      ocrResult.setRequestIdentifier(request.getRequestIdentifier());
    }
    ocrResult.setStatus(OcrStatus.CONFIRMED);
    ocrResult.setConfirmedAt(LocalDateTime.now());

    return mapToResponse(stagingOcrResultRepository.save(ocrResult));
  }

  @Override
  public boolean isConfirmed(String sessionToken) {
    OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);
    return stagingOcrResultRepository.existsByOnboardingSession_IdAndStatus(
        session.getId(), OcrStatus.CONFIRMED);
  }

  private void ensureNameMatchesExtraction(
      String fieldLabel, String extractedValue, String submittedValue) {
    if (!isUsable(extractedValue)) {
      return;
    }
    if (StringSimilarity.similarity(extractedValue, submittedValue) < ocrNameSimilarityThreshold) {
      throw new BusinessException(
          "Le "
              + fieldLabel
              + " saisi ne correspond pas à celui lu sur votre document. Corrigez la saisie ou reprenez les photos.");
    }
  }

  private void ensureDocumentNumberMatchesExtraction(String extractedValue, String submittedValue) {
    if (!isUsable(extractedValue)) {
      return;
    }
    String normalizedExtracted = normalizeDocumentNumber(extractedValue);
    String normalizedSubmitted = normalizeDocumentNumber(submittedValue);
    if (StringSimilarity.similarity(normalizedExtracted, normalizedSubmitted)
        < DOCUMENT_NUMBER_SIMILARITY_THRESHOLD) {
      throw new BusinessException(
          "Le numéro de document saisi ne correspond pas à celui lu sur votre CNI.");
    }
  }

  private void ensureBirthDateMatchesExtraction(
      LocalDate extractedValue, LocalDate submittedValue) {
    if (extractedValue == null) {
      return;
    }
    if (!extractedValue.equals(submittedValue)) {
      throw new BusinessException(
          "La date de naissance saisie ne correspond pas à celle lue sur votre document.");
    }
  }

  private void ensureMatchesAccountHolder(
      String accountFirstName, String accountLastName, OcrConfirmationRequest request) {
    double firstNameSimilarity =
        StringSimilarity.similarity(accountFirstName, request.getFirstName());
    double lastNameSimilarity = StringSimilarity.similarity(accountLastName, request.getLastName());

    if (firstNameSimilarity < identityNameSimilarityThreshold
        || lastNameSimilarity < identityNameSimilarityThreshold) {
      throw new BusinessException(
          "L'identité extraite du document ne correspond pas au titulaire du compte bancaire. Contactez le support si vous pensez qu'il s'agit d'une erreur.",
          TypeErreurMetier.CONFLIT);
    }
  }

  private static boolean isUsable(String value) {
    return value != null && !value.isBlank() && !value.equalsIgnoreCase(NOT_EXTRACTED_MARKER);
  }

  private static String normalizeDocumentNumber(String value) {
    return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
  }

  private StagingOcrResult findExistingResult(UUID onboardingSessionId) {
    return stagingOcrResultRepository
        .findByOnboardingSession_Id(onboardingSessionId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Aucune extraction OCR trouvée pour ce client"));
  }

  private OcrExtractionResponse mapToResponse(StagingOcrResult ocrResult) {
    return OcrExtractionResponse.builder()
        .documentOcrResultId(ocrResult.getId())
        .documentKind(ocrResult.getDocumentKind())
        .firstName(ocrResult.getFirstName())
        .lastName(ocrResult.getLastName())
        .documentNumber(ocrResult.getDocumentNumber())
        .sex(ocrResult.getSex())
        .birthDate(ocrResult.getBirthDate())
        .expiryDate(ocrResult.getExpiryDate())
        .birthPlace(ocrResult.getBirthPlace())
        .fatherName(ocrResult.getFatherName())
        .motherName(ocrResult.getMotherName())
        .kitNumber(ocrResult.getKitNumber())
        .requestIdentifier(ocrResult.getRequestIdentifier())
        .paymentAmount(ocrResult.getPaymentAmount())
        .paymentDate(ocrResult.getPaymentDate())
        .confidenceScore(ocrResult.getConfidenceScore())
        .documentQualityScore(ocrResult.getDocumentQualityScore())
        .status(ocrResult.getStatus().name())
        .provider(ocrResult.getOcrProvider())
        .build();
  }
}
