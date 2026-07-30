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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FaceVerificationServiceImplTest {

    @Mock
    private StagingDocumentRepository stagingDocumentRepository;
    @Mock
    private StagingFaceVerificationResultRepository stagingFaceVerificationResultRepository;
    @Mock
    private OnboardingSessionService onboardingSessionService;
    @Mock
    private StorageService storageService;
    @Mock
    private FaceMatchProvider faceMatchProvider;

    private FaceVerificationServiceImpl service;

    private final String sessionToken = "session-token";
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FaceVerificationServiceImpl(stagingDocumentRepository, stagingFaceVerificationResultRepository, onboardingSessionService, storageService, faceMatchProvider);
    }

    private OnboardingSession session() {
        return OnboardingSession.builder().id(sessionId).build();
    }

    @Test
    void verifyFaceThrowsWhenCniPhotoMissing() {
        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session());
        when(stagingDocumentRepository.findByOnboardingSession_IdAndDocumentType(sessionId, DocumentType.CNI_RECTO))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyFace(sessionToken))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("recto");
    }

    @Test
    void verifyFaceSavesVerifiedResultWhenFacesMatch() {
        OnboardingSession session = session();
        StagingDocument cniPhoto = StagingDocument.builder().filePath("/cni.jpg").build();
        StagingDocument selfie = StagingDocument.builder().filePath("/selfie.jpg").build();

        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session);
        when(stagingDocumentRepository.findByOnboardingSession_IdAndDocumentType(sessionId, DocumentType.CNI_RECTO))
            .thenReturn(Optional.of(cniPhoto));
        when(stagingDocumentRepository.findByOnboardingSession_IdAndDocumentType(sessionId, DocumentType.SELFIE))
            .thenReturn(Optional.of(selfie));
        when(storageService.read("/cni.jpg")).thenReturn(new byte[]{1});
        when(storageService.read("/selfie.jpg")).thenReturn(new byte[]{2});
        when(faceMatchProvider.compareFaces(any(), any())).thenReturn(new FaceMatchResult(true, 92.5, 88.0));
        when(faceMatchProvider.getProviderName()).thenReturn("MOCK");
        when(stagingFaceVerificationResultRepository.findByOnboardingSession_Id(sessionId)).thenReturn(Optional.empty());
        when(stagingFaceVerificationResultRepository.save(any(StagingFaceVerificationResult.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        FaceVerificationResponse response = service.verifyFace(sessionToken);

        assertThat(response.isMatched()).isTrue();
        assertThat(response.getSimilarityScore()).isEqualTo(92.5);
        assertThat(response.getStatus()).isEqualTo(FaceVerificationStatus.VERIFIED.name());
    }

    @Test
    void verifyFaceThrowsAndPersistsFailedResultWhenFacesDoNotMatch() {
        OnboardingSession session = session();
        StagingDocument cniPhoto = StagingDocument.builder().filePath("/cni.jpg").build();
        StagingDocument selfie = StagingDocument.builder().filePath("/selfie.jpg").build();

        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session);
        when(stagingDocumentRepository.findByOnboardingSession_IdAndDocumentType(sessionId, DocumentType.CNI_RECTO))
            .thenReturn(Optional.of(cniPhoto));
        when(stagingDocumentRepository.findByOnboardingSession_IdAndDocumentType(sessionId, DocumentType.SELFIE))
            .thenReturn(Optional.of(selfie));
        when(storageService.read("/cni.jpg")).thenReturn(new byte[]{1});
        when(storageService.read("/selfie.jpg")).thenReturn(new byte[]{2});
        when(faceMatchProvider.compareFaces(any(), any())).thenReturn(new FaceMatchResult(false, 12.0, 55.0));
        when(faceMatchProvider.getProviderName()).thenReturn("MOCK");
        when(stagingFaceVerificationResultRepository.findByOnboardingSession_Id(sessionId)).thenReturn(Optional.empty());
        when(stagingFaceVerificationResultRepository.save(any(StagingFaceVerificationResult.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.verifyFace(sessionToken))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("ne correspond pas");

        verify(stagingFaceVerificationResultRepository).save(argThat(saved -> saved.getStatus() == FaceVerificationStatus.FAILED));
    }

    @Test
    void getVerificationThrowsWhenNoResultExists() {
        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session());
        when(stagingFaceVerificationResultRepository.findByOnboardingSession_Id(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVerification(sessionToken))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void isVerifiedDelegatesToRepository() {
        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session());
        when(stagingFaceVerificationResultRepository.existsByOnboardingSession_IdAndStatus(sessionId, FaceVerificationStatus.VERIFIED))
            .thenReturn(true);

        assertThat(service.isVerified(sessionToken)).isTrue();
    }
}
