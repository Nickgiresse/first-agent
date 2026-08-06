package com.firstagent.backend.onboarding.service;

import com.firstagent.backend.account.entity.BankAccount;
import com.firstagent.backend.whatsappbanking.client.WhatsAppBankingClient;
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
import com.firstagent.backend.onboarding.dto.KycRequest;
import com.firstagent.backend.onboarding.dto.OnboardingCompletionResponse;
import com.firstagent.backend.onboarding.dto.OtpVerificationRequest;
import com.firstagent.backend.onboarding.entity.Customer;
import com.firstagent.backend.onboarding.entity.OnboardingSession;
import com.firstagent.backend.onboarding.repository.CustomerRepository;
import com.firstagent.backend.onboarding.repository.OnboardingSessionRepository;
import com.firstagent.backend.pin.service.PinService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JavaMailSender mailSender;
    @Mock private DocumentService documentService;
    @Mock private OcrService ocrService;
    @Mock private FaceVerificationService faceVerificationService;
    @Mock private LivenessService livenessService;
    @Mock private WhatsAppBankingClient whatsAppBankingClient;
    @Mock private StagingDocumentRepository stagingDocumentRepository;
    @Mock private StagingOcrResultRepository stagingOcrResultRepository;
    @Mock private StagingFaceVerificationResultRepository stagingFaceVerificationResultRepository;
    @Mock private StagingLivenessResultRepository stagingLivenessResultRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentOcrResultRepository documentOcrResultRepository;
    @Mock private FaceVerificationResultRepository faceVerificationResultRepository;
    @Mock private LivenessResultRepository livenessResultRepository;
    @Mock private OnboardingSessionRepository onboardingSessionRepository;

    private OnboardingServiceImpl service;

    private final UUID sessionId = UUID.randomUUID();
    private final String sessionToken = "session-token";

    @BeforeEach
    void setUp() {
        service = new OnboardingServiceImpl(
            onboardingSessionService, customerRepository, pinService, passwordEncoder, mailSender, documentService, ocrService,
            faceVerificationService, livenessService, whatsAppBankingClient,
            stagingDocumentRepository, stagingOcrResultRepository, stagingFaceVerificationResultRepository, stagingLivenessResultRepository,
            documentRepository, documentOcrResultRepository, faceVerificationResultRepository, livenessResultRepository,
            onboardingSessionRepository
        );
        ReflectionTestUtils.setField(service, "manualReviewThreshold", 70.0);
        ReflectionTestUtils.setField(service, "fromAddress", "onboarding@afrilandfirstbank.com");
    }

    private OnboardingSession accountVerifiedSession() {
        BankAccount bankAccount = BankAccount.builder().firstName("Jean").lastName("Nkeng").build();
        return OnboardingSession.builder()
            .id(sessionId)
            .bankAccount(bankAccount)
            .status(OnboardingStatus.ACCOUNT_VERIFIED)
            .emailOtpAttempts(0)
            .build();
    }

    @Test
    void requestEmailOtpSendsCodeAndStoresHash() {
        OnboardingSession session = accountVerifiedSession();
        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session);
        when(customerRepository.existsByEmail("jean@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed-code");

        service.requestEmailOtp(sessionToken, kycRequest("jean@example.com"));

        ArgumentCaptor<OnboardingSession> captor = ArgumentCaptor.forClass(OnboardingSession.class);
        verify(onboardingSessionRepository).save(captor.capture());
        OnboardingSession saved = captor.getValue();
        assertThat(saved.getPendingEmail()).isEqualTo("jean@example.com");
        assertThat(saved.getEmailOtpCodeHash()).isEqualTo("hashed-code");
        assertThat(saved.getEmailOtpExpiresAt()).isAfter(LocalDateTime.now());

        verify(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    void requestEmailOtpThrowsWhenEmailAlreadyUsed() {
        OnboardingSession session = accountVerifiedSession();
        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session);
        when(customerRepository.existsByEmail("jean@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.requestEmailOtp(sessionToken, kycRequest("jean@example.com")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("déjà utilisée");

        verifyNoInteractions(mailSender);
    }

    @Test
    void requestEmailOtpThrowsDuringResendCooldown() {
        OnboardingSession session = accountVerifiedSession();
        session.setEmailOtpLastSentAt(LocalDateTime.now());
        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session);

        assertThatThrownBy(() -> service.requestEmailOtp(sessionToken, kycRequest("jean@example.com")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("patienter");

        verifyNoInteractions(mailSender);
    }

    @Test
    void verifyEmailOtpCompletesKycOnCorrectCode() {
        OnboardingSession session = accountVerifiedSession();
        session.setPendingEmail("jean@example.com");
        session.setEmailOtpCodeHash("hashed-code");
        session.setEmailOtpExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session);
        when(passwordEncoder.matches("123456", "hashed-code")).thenReturn(true);

        service.verifyEmailOtp(sessionToken, otpRequest("123456"));

        assertThat(session.getEmail()).isEqualTo("jean@example.com");
        assertThat(session.getPendingEmail()).isNull();
        assertThat(session.getEmailOtpCodeHash()).isNull();
        verify(onboardingSessionService).updateStatus(session, OnboardingStatus.KYC_COMPLETED);
    }

    @Test
    void verifyEmailOtpThrowsAndIncrementsAttemptsOnWrongCode() {
        OnboardingSession session = accountVerifiedSession();
        session.setPendingEmail("jean@example.com");
        session.setEmailOtpCodeHash("hashed-code");
        session.setEmailOtpExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session);
        when(passwordEncoder.matches("000000", "hashed-code")).thenReturn(false);

        assertThatThrownBy(() -> service.verifyEmailOtp(sessionToken, otpRequest("000000")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("incorrect");

        assertThat(session.getEmailOtpAttempts()).isEqualTo(1);
        verify(onboardingSessionService, never()).updateStatus(any(), any());
    }

    @Test
    void verifyEmailOtpThrowsWhenExpired() {
        OnboardingSession session = accountVerifiedSession();
        session.setPendingEmail("jean@example.com");
        session.setEmailOtpCodeHash("hashed-code");
        session.setEmailOtpExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session);

        assertThatThrownBy(() -> service.verifyEmailOtp(sessionToken, otpRequest("123456")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("expiré");
    }

    @Test
    void verifyEmailOtpThrowsAfterMaxAttempts() {
        OnboardingSession session = accountVerifiedSession();
        session.setPendingEmail("jean@example.com");
        session.setEmailOtpCodeHash("hashed-code");
        session.setEmailOtpExpiresAt(LocalDateTime.now().plusMinutes(5));
        session.setEmailOtpAttempts(5);
        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session);

        assertThatThrownBy(() -> service.verifyEmailOtp(sessionToken, otpRequest("123456")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Trop de tentatives");
    }

    // Les deux tests du contournement /kyc/skip sont retirés avec la
    // fonctionnalité : ils vérifiaient précisément qu'une session pouvait
    // atteindre KYC_COMPLETED sans adresse e-mail, ce qui n'est plus un
    // comportement recherché. L'envoi fonctionne, seule MAIL_PASSWORD manquait.

    private static KycRequest kycRequest(String email) {
        KycRequest request = new KycRequest();
        request.setEmail(email);
        return request;
    }

    private static OtpVerificationRequest otpRequest(String code) {
        OtpVerificationRequest request = new OtpVerificationRequest();
        request.setCode(code);
        return request;
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

        assertThatThrownBy(() -> service.completeOnboarding(sessionToken, null))
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

        OnboardingCompletionResponse response = service.completeOnboarding(sessionToken, null);

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

        OnboardingCompletionResponse response = service.completeOnboarding(sessionToken, null);

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
