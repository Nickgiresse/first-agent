package com.firstagent.backend.onboarding.service;

import com.firstagent.backend.account.entity.BankAccount;
import com.firstagent.backend.common.enums.DocumentType;
import com.firstagent.backend.common.enums.FaceVerificationStatus;
import com.firstagent.backend.common.enums.LivenessStatus;
import com.firstagent.backend.common.enums.OcrStatus;
import com.firstagent.backend.common.enums.OnboardingStatus;
import com.firstagent.backend.common.exception.BusinessException;
import com.firstagent.backend.document.entity.CustomerDocument;
import com.firstagent.backend.document.entity.DocumentOcrResult;
import com.firstagent.backend.document.entity.FaceVerificationResult;
import com.firstagent.backend.document.entity.StagingDocument;
import com.firstagent.backend.document.entity.StagingFaceVerificationResult;
import com.firstagent.backend.document.entity.StagingOcrResult;
import com.firstagent.backend.document.repository.DocumentOcrResultRepository;
import com.firstagent.backend.document.repository.DocumentRepository;
import com.firstagent.backend.document.repository.FaceVerificationResultRepository;
import com.firstagent.backend.document.repository.StagingDocumentRepository;
import com.firstagent.backend.document.repository.StagingFaceVerificationResultRepository;
import com.firstagent.backend.document.repository.StagingOcrResultRepository;
import com.firstagent.backend.document.service.DocumentService;
import com.firstagent.backend.document.service.FaceVerificationService;
import com.firstagent.backend.document.service.OcrService;
import com.firstagent.backend.liveness.entity.LivenessResult;
import com.firstagent.backend.liveness.entity.StagingLivenessResult;
import com.firstagent.backend.liveness.repository.LivenessResultRepository;
import com.firstagent.backend.liveness.repository.StagingLivenessResultRepository;
import com.firstagent.backend.liveness.service.LivenessService;
import com.firstagent.backend.onboarding.dto.OnboardingCompletionResponse;
import com.firstagent.backend.onboarding.entity.Customer;
import com.firstagent.backend.onboarding.entity.OnboardingSession;
import com.firstagent.backend.onboarding.repository.CustomerRepository;
import com.firstagent.backend.pin.service.PinService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceImplTest {

    @Mock private OnboardingSessionService onboardingSessionService;
    @Mock private CustomerRepository customerRepository;
    @Mock private PinService pinService;
    @Mock private DocumentService documentService;
    @Mock private OcrService ocrService;
    @Mock private FaceVerificationService faceVerificationService;
    @Mock private LivenessService livenessService;
    @Mock private StagingDocumentRepository stagingDocumentRepository;
    @Mock private StagingOcrResultRepository stagingOcrResultRepository;
    @Mock private StagingFaceVerificationResultRepository stagingFaceVerificationResultRepository;
    @Mock private StagingLivenessResultRepository stagingLivenessResultRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentOcrResultRepository documentOcrResultRepository;
    @Mock private FaceVerificationResultRepository faceVerificationResultRepository;
    @Mock private LivenessResultRepository livenessResultRepository;

    private OnboardingServiceImpl service;

    private final UUID sessionId = UUID.randomUUID();
    private final String sessionToken = "session-token";

    @BeforeEach
    void setUp() {
        service = new OnboardingServiceImpl(
            onboardingSessionService, customerRepository, pinService, documentService, ocrService,
            faceVerificationService, livenessService,
            stagingDocumentRepository, stagingOcrResultRepository, stagingFaceVerificationResultRepository, stagingLivenessResultRepository,
            documentRepository, documentOcrResultRepository, faceVerificationResultRepository, livenessResultRepository
        );
        ReflectionTestUtils.setField(service, "manualReviewThreshold", 70.0);
    }

    private OnboardingSession termsAcceptedSession() {
        BankAccount bankAccount = BankAccount.builder().firstName("Jean").lastName("Nkeng").build();
        return OnboardingSession.builder()
            .id(sessionId)
            .bankAccount(bankAccount)
            .status(OnboardingStatus.TERMS_ACCEPTED)
            .email("jean@example.com")
            .pinHash("hashed-pin")
            .termsAccepted(true)
            .termsAcceptedAt(LocalDateTime.now())
            .build();
    }

    private List<StagingDocument> stagedDocuments(OnboardingSession session) {
        return List.of(
            StagingDocument.builder().onboardingSession(session).documentType(DocumentType.CNI_RECTO)
                .filePath("/recto.jpg").fileName("recto.jpg").mimeType("image/jpeg").fileSize(100L).build(),
            StagingDocument.builder().onboardingSession(session).documentType(DocumentType.CNI_VERSO)
                .filePath("/verso.jpg").fileName("verso.jpg").mimeType("image/jpeg").fileSize(100L).build(),
            StagingDocument.builder().onboardingSession(session).documentType(DocumentType.SELFIE)
                .filePath("/selfie.jpg").fileName("selfie.jpg").mimeType("image/jpeg").fileSize(100L).build()
        );
    }

    private StagingOcrResult stagedOcr(OnboardingSession session, Double documentQualityScore) {
        return StagingOcrResult.builder()
            .onboardingSession(session)
            .documentKind("CNI")
            .firstName("Jean")
            .lastName("Nkeng")
            .ocrProvider("PYTHON_VISION")
            .status(OcrStatus.CONFIRMED)
            .documentQualityScore(documentQualityScore)
            .build();
    }

    private StagingFaceVerificationResult stagedFace(OnboardingSession session, Double targetQualityScore) {
        return StagingFaceVerificationResult.builder()
            .onboardingSession(session)
            .provider("PYTHON_VISION")
            .status(FaceVerificationStatus.VERIFIED)
            .similarityScore(95.0)
            .targetQualityScore(targetQualityScore)
            .build();
    }

    private StagingLivenessResult stagedLiveness(OnboardingSession session) {
        return StagingLivenessResult.builder()
            .onboardingSession(session)
            .sessionId("liveness-session")
            .status(LivenessStatus.LIVE)
            .completedActions("BLINK,TURN_LEFT")
            .totalActions(2)
            .build();
    }

    @Test
    void completeOnboardingThrowsWhenTermsNotAccepted() {
        OnboardingSession session = OnboardingSession.builder().id(sessionId).status(OnboardingStatus.PIN_CREATED).build();
        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session);

        assertThatThrownBy(() -> service.completeOnboarding(sessionToken))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("conditions");

        verifyNoInteractions(customerRepository);
    }

    @Test
    void completeOnboardingMaterializesAllFinalEntitiesOnHappyPath() {
        OnboardingSession session = termsAcceptedSession();
        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session);
        when(stagingDocumentRepository.findByOnboardingSession_Id(sessionId)).thenReturn(stagedDocuments(session));
        when(stagingOcrResultRepository.findByOnboardingSession_Id(sessionId)).thenReturn(Optional.of(stagedOcr(session, 85.0)));
        when(stagingFaceVerificationResultRepository.findByOnboardingSession_Id(sessionId)).thenReturn(Optional.of(stagedFace(session, 90.0)));
        when(stagingLivenessResultRepository.findByOnboardingSession_Id(sessionId)).thenReturn(Optional.of(stagedLiveness(session)));

        OnboardingCompletionResponse response = service.completeOnboarding(sessionToken);

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        Customer savedCustomer = customerCaptor.getValue();
        assertThat(savedCustomer.getEmail()).isEqualTo("jean@example.com");
        assertThat(savedCustomer.getPinHash()).isEqualTo("hashed-pin");
        assertThat(savedCustomer.getFirstName()).isEqualTo("Jean");
        assertThat(savedCustomer.isTermsAccepted()).isTrue();
        assertThat(savedCustomer.isRequiresManualReview()).isFalse();

        verify(documentRepository, times(3)).save(any(CustomerDocument.class));
        verify(documentOcrResultRepository).save(any(DocumentOcrResult.class));
        verify(faceVerificationResultRepository).save(any(FaceVerificationResult.class));
        verify(livenessResultRepository).save(any(LivenessResult.class));

        verify(stagingDocumentRepository).deleteByOnboardingSession_Id(sessionId);
        verify(stagingOcrResultRepository).deleteByOnboardingSession_Id(sessionId);
        verify(stagingFaceVerificationResultRepository).deleteByOnboardingSession_Id(sessionId);
        verify(stagingLivenessResultRepository).deleteByOnboardingSession_Id(sessionId);

        verify(onboardingSessionService).updateStatus(session, OnboardingStatus.COMPLETED);
        assertThat(response.isRequiresManualReview()).isFalse();
    }

    @Test
    void completeOnboardingFlagsManualReviewWhenQualityBelowThresholdButStillCompletes() {
        OnboardingSession session = termsAcceptedSession();
        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session);
        when(stagingDocumentRepository.findByOnboardingSession_Id(sessionId)).thenReturn(stagedDocuments(session));
        when(stagingOcrResultRepository.findByOnboardingSession_Id(sessionId)).thenReturn(Optional.of(stagedOcr(session, 50.0)));
        when(stagingFaceVerificationResultRepository.findByOnboardingSession_Id(sessionId)).thenReturn(Optional.of(stagedFace(session, 90.0)));
        when(stagingLivenessResultRepository.findByOnboardingSession_Id(sessionId)).thenReturn(Optional.of(stagedLiveness(session)));

        OnboardingCompletionResponse response = service.completeOnboarding(sessionToken);

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        Customer savedCustomer = customerCaptor.getValue();
        assertThat(savedCustomer.isRequiresManualReview()).isTrue();
        assertThat(savedCustomer.getManualReviewReason()).contains("document d'identité");

        // le dossier est tout de même finalisé malgré la qualité insuffisante
        assertThat(response.isRequiresManualReview()).isTrue();
        verify(onboardingSessionService).updateStatus(session, OnboardingStatus.COMPLETED);
    }
}
