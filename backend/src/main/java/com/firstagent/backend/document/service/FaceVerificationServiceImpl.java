package com.firstagent.backend.document.service;

import com.firstagent.backend.common.enums.DocumentType;
import com.firstagent.backend.common.enums.FaceVerificationStatus;
import com.firstagent.backend.common.exception.BusinessException;
import com.firstagent.backend.common.exception.ResourceNotFoundException;
import com.firstagent.backend.document.dto.FaceVerificationResponse;
import com.firstagent.backend.document.entity.StagingDocument;
import com.firstagent.backend.document.entity.StagingFaceVerificationResult;
import com.firstagent.backend.document.repository.StagingDocumentRepository;
import com.firstagent.backend.document.repository.StagingFaceVerificationResultRepository;
import com.firstagent.backend.onboarding.entity.OnboardingSession;
import com.firstagent.backend.onboarding.service.OnboardingSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FaceVerificationServiceImpl implements FaceVerificationService {

    private final StagingDocumentRepository stagingDocumentRepository;
    private final StagingFaceVerificationResultRepository stagingFaceVerificationResultRepository;
    private final OnboardingSessionService onboardingSessionService;
    private final StorageService storageService;
    private final FaceMatchProvider faceMatchProvider;

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public FaceVerificationResponse verifyFace(String sessionToken) {
        OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);

        StagingDocument cniPhoto = stagingDocumentRepository.findByOnboardingSession_IdAndDocumentType(session.getId(), DocumentType.CNI_RECTO)
            .orElseThrow(() -> new BusinessException("Le recto de la CNI doit être téléversé avant la vérification faciale"));
        StagingDocument selfie = stagingDocumentRepository.findByOnboardingSession_IdAndDocumentType(session.getId(), DocumentType.SELFIE)
            .orElseThrow(() -> new BusinessException("Le selfie doit être téléversé avant la vérification faciale"));

        byte[] cniPhotoBytes = storageService.read(cniPhoto.getFilePath());
        byte[] selfieBytes = storageService.read(selfie.getFilePath());

        FaceMatchResult matchResult = faceMatchProvider.compareFaces(cniPhotoBytes, selfieBytes);

        StagingFaceVerificationResult result = stagingFaceVerificationResultRepository.findByOnboardingSession_Id(session.getId())
            .orElseGet(() -> StagingFaceVerificationResult.builder().onboardingSession(session).build());

        result.setSimilarityScore(matchResult.similarityScore());
        result.setTargetQualityScore(matchResult.targetQualityScore());
        result.setProvider(faceMatchProvider.getProviderName());
        result.setStatus(matchResult.matched() ? FaceVerificationStatus.VERIFIED : FaceVerificationStatus.FAILED);
        result.setVerifiedAt(LocalDateTime.now());

        FaceVerificationResponse response = mapToResponse(stagingFaceVerificationResultRepository.save(result));

        if (!matchResult.matched()) {
            throw new BusinessException(
                "Le visage sur le selfie ne correspond pas à la photo de la CNI. Reprenez la photo dans de meilleures conditions (visage bien visible, sans lunettes de soleil ni masque)."
            );
        }

        return response;
    }

    @Override
    public FaceVerificationResponse getVerification(String sessionToken) {
        OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);
        return mapToResponse(
            stagingFaceVerificationResultRepository.findByOnboardingSession_Id(session.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Aucune vérification faciale trouvée pour ce client"))
        );
    }

    @Override
    public boolean isVerified(String sessionToken) {
        OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);
        return stagingFaceVerificationResultRepository.existsByOnboardingSession_IdAndStatus(session.getId(), FaceVerificationStatus.VERIFIED);
    }

    private FaceVerificationResponse mapToResponse(StagingFaceVerificationResult result) {
        return FaceVerificationResponse.builder()
            .faceVerificationResultId(result.getId())
            .matched(result.getStatus() == FaceVerificationStatus.VERIFIED)
            .similarityScore(result.getSimilarityScore())
            .targetQualityScore(result.getTargetQualityScore())
            .status(result.getStatus().name())
            .provider(result.getProvider())
            .verifiedAt(result.getVerifiedAt())
            .build();
    }
}
