package com.firstagent.backend.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.firstagent.backend.account.entity.BankAccount;
import com.firstagent.backend.audit.model.EvenementAudit;
import com.firstagent.backend.audit.port.JournalAudit;
import com.firstagent.backend.common.enums.CustomerStatus;
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
import com.firstagent.backend.document.service.StorageService;
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
import com.firstagent.backend.pin.port.PinService;
import com.firstagent.backend.whatsappbanking.client.WhatsAppBankingClient;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceImplTest {

  @Mock private JournalAudit journal;
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
  @Mock private StorageService storageService;
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
    service =
        new OnboardingServiceImpl(
            journal,
            onboardingSessionService,
            customerRepository,
            pinService,
            passwordEncoder,
            mailSender,
            documentService,
            ocrService,
            faceVerificationService,
            livenessService,
            whatsAppBankingClient,
            storageService,
            stagingDocumentRepository,
            stagingOcrResultRepository,
            stagingFaceVerificationResultRepository,
            stagingLivenessResultRepository,
            documentRepository,
            documentOcrResultRepository,
            faceVerificationResultRepository,
            livenessResultRepository,
            onboardingSessionRepository);
    ReflectionTestUtils.setField(service, "manualReviewThreshold", 70.0);
    // Seuil de confiance faciale : sans injection il vaudrait zero, et aucune
    // ressemblance faible ne serait jamais signalee.
    ReflectionTestUtils.setField(service, "faceConfidenceThreshold", 75.0);
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

  @Test
  @DisplayName("le compteur de tentatives survit au rejet, sans quoi le verrou ne servirait à rien")
  void verifyEmailOtp_leCompteurDeTentativesNEstPasAnnuleParLeRejet() throws NoSuchMethodException {
    // Ce test porte sur l'annotation, parce que c'est là que siégeait le
    // défaut. BusinessException étant une exception non contrôlée, une
    // transaction ordinaire annulait tout, y compris l'incrément du compteur
    // écrit juste avant le rejet : le compteur repartait de zéro à chaque
    // essai, le verrou n'était jamais atteint, et les six chiffres du code
    // pouvaient être parcourus sans limite. La protection tenait donc
    // entièrement à cette clause.
    Transactional annotation =
        OnboardingServiceImpl.class
            .getMethod("verifyEmailOtp", String.class, OtpVerificationRequest.class)
            .getAnnotation(Transactional.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.noRollbackFor()).contains(BusinessException.class);
  }

  @Test
  @DisplayName("un code erroné est journalisé, sans le code saisi")
  void verifyEmailOtp_codeErrone_estJournalise() {
    OnboardingSession session = accountVerifiedSession();
    session.setPendingEmail("jean@example.com");
    session.setEmailOtpCodeHash("hashed-code");
    session.setEmailOtpExpiresAt(LocalDateTime.now().plusMinutes(5));
    session.setEmailOtpAttempts(0);
    when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session);
    when(passwordEncoder.matches("000000", "hashed-code")).thenReturn(false);

    assertThatThrownBy(() -> service.verifyEmailOtp(sessionToken, otpRequest("000000")))
        .isInstanceOf(BusinessException.class);

    ArgumentCaptor<JournalAudit.EcritureAudit> trace =
        ArgumentCaptor.forClass(JournalAudit.EcritureAudit.class);
    verify(journal).enregistrer(trace.capture());
    assertThat(trace.getValue().typeEvenement()).isEqualTo(EvenementAudit.OTP_ERRONE);
    // Le rang de la tentative, jamais le code : journaliser les codes essayés
    // livrerait par recoupement l'espace parcouru à qui lit le journal.
    assertThat(trace.getValue().details()).contains("tentative 1").doesNotContain("000000");
  }

  @Test
  @DisplayName("le verrouillage est journalisé à part du simple code erroné")
  void verifyEmailOtp_verrouillage_estJournaliseAPart() {
    OnboardingSession session = accountVerifiedSession();
    session.setPendingEmail("jean@example.com");
    session.setEmailOtpCodeHash("hashed-code");
    session.setEmailOtpExpiresAt(LocalDateTime.now().plusMinutes(5));
    session.setEmailOtpAttempts(5);
    when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session);

    assertThatThrownBy(() -> service.verifyEmailOtp(sessionToken, otpRequest("123456")))
        .isInstanceOf(BusinessException.class);

    ArgumentCaptor<JournalAudit.EcritureAudit> trace =
        ArgumentCaptor.forClass(JournalAudit.EcritureAudit.class);
    verify(journal).enregistrer(trace.capture());
    // Deux événements distincts : les confondre empêcherait de les compter
    // séparément, alors qu'atteindre le verrou est un signal bien plus fort.
    assertThat(trace.getValue().typeEvenement()).isEqualTo(EvenementAudit.OTP_VERROUILLE);
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
        StagingDocument.builder()
            .onboardingSession(session)
            .documentType(DocumentType.CNI_RECTO)
            .filePath("/recto.jpg")
            .fileName("recto.jpg")
            .mimeType("image/jpeg")
            .fileSize(100L)
            .build(),
        StagingDocument.builder()
            .onboardingSession(session)
            .documentType(DocumentType.CNI_VERSO)
            .filePath("/verso.jpg")
            .fileName("verso.jpg")
            .mimeType("image/jpeg")
            .fileSize(100L)
            .build(),
        StagingDocument.builder()
            .onboardingSession(session)
            .documentType(DocumentType.SELFIE)
            .filePath("/selfie.jpg")
            .fileName("selfie.jpg")
            .mimeType("image/jpeg")
            .fileSize(100L)
            .build());
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

  private StagingFaceVerificationResult stagedFace(
      OnboardingSession session, Double targetQualityScore) {
    return stagedFace(session, targetQualityScore, 95.0);
  }

  private StagingFaceVerificationResult stagedFace(
      OnboardingSession session, Double targetQualityScore, Double similarityScore) {
    return StagingFaceVerificationResult.builder()
        .onboardingSession(session)
        .provider("PYTHON_VISION")
        .status(FaceVerificationStatus.VERIFIED)
        .similarityScore(similarityScore)
        .targetQualityScore(targetQualityScore)
        .build();
  }

  /** Prépare un dossier complet, en maîtrisant la seule similarité faciale. */
  private void dossierPretAvecSimilarite(double similarite) {
    OnboardingSession session = termsAcceptedSession();
    when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session);
    when(stagingDocumentRepository.findByOnboardingSession_Id(sessionId))
        .thenReturn(stagedDocuments(session));
    when(stagingOcrResultRepository.findByOnboardingSession_Id(sessionId))
        .thenReturn(Optional.of(stagedOcr(session, 85.0)));
    when(stagingFaceVerificationResultRepository.findByOnboardingSession_Id(sessionId))
        .thenReturn(Optional.of(stagedFace(session, 90.0, similarite)));
    when(stagingLivenessResultRepository.findByOnboardingSession_Id(sessionId))
        .thenReturn(Optional.of(stagedLiveness(session)));
  }

  private Customer clientEnregistre() {
    ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
    verify(customerRepository).save(captor.capture());
    return captor.getValue();
  }

  @Test
  @DisplayName("une ressemblance faciale nette n'appelle aucune révision")
  void completion_ressemblanceNette_aboutitSansReserve() {
    dossierPretAvecSimilarite(95.0);

    service.completeOnboarding(sessionToken, null);

    assertThat(clientEnregistre().isRequiresManualReview()).isFalse();
  }

  @Test
  @DisplayName("une ressemblance faible renvoie le dossier en agence")
  void completion_ressemblanceFaible_renvoieEnAgence() {
    // Le service de vision a ACCEPTÉ le rapprochement : le parcours va jusqu'au
    // bout. Mais accepté de justesse n'est pas certain, et c'est exactement là
    // que se logent les cas douteux : ressemblance familiale, photo ancienne,
    // éclairage défavorable. Les approuver automatiquement reviendrait à
    // trancher à la place d'un humain.
    dossierPretAvecSimilarite(62.0);

    service.completeOnboarding(sessionToken, null);

    Customer client = clientEnregistre();
    assertThat(client.isRequiresManualReview()).isTrue();
    assertThat(client.getManualReviewReason()).contains("agence");
  }

  @Test
  @DisplayName("un dossier sans réserve ouvre l'accès au service")
  void completion_sansReserve_clientActif() {
    dossierPretAvecSimilarite(95.0);

    service.completeOnboarding(sessionToken, null);

    Customer client = clientEnregistre();
    assertThat(client.getStatus()).isEqualTo(CustomerStatus.USER);
    assertThat(client.getStatus().estActif()).isTrue();
  }

  @Test
  @DisplayName("un dossier en révision n'ouvre pas l'accès au service")
  void completion_enRevision_clientInactif() {
    // Le drapeau de révision existait déjà, mais n'empêchait rien : le dossier
    // naissait actif et le client utilisait le service pendant qu'on attendait
    // précisément la confirmation de son identité.
    dossierPretAvecSimilarite(62.0);

    service.completeOnboarding(sessionToken, null);

    Customer client = clientEnregistre();
    assertThat(client.getStatus()).isEqualTo(CustomerStatus.PENDING_REVIEW);
    assertThat(client.getStatus().estActif()).isFalse();
  }

  @Test
  @DisplayName("le dossier est enregistré malgré la réserve, il n'est pas rejeté")
  void completion_ressemblanceFaible_enregistreQuandMeme() {
    // Rejeter obligerait le client à tout recommencer alors que rien ne prouve
    // une fraude. Le dossier existe, il attend seulement une confirmation.
    dossierPretAvecSimilarite(62.0);

    OnboardingCompletionResponse reponse = service.completeOnboarding(sessionToken, null);

    assertThat(reponse.isRequiresManualReview()).isTrue();
    verify(customerRepository).save(any(Customer.class));
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
    OnboardingSession session =
        OnboardingSession.builder().id(sessionId).status(OnboardingStatus.PIN_CREATED).build();
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
    when(stagingDocumentRepository.findByOnboardingSession_Id(sessionId))
        .thenReturn(stagedDocuments(session));
    when(stagingOcrResultRepository.findByOnboardingSession_Id(sessionId))
        .thenReturn(Optional.of(stagedOcr(session, 85.0)));
    when(stagingFaceVerificationResultRepository.findByOnboardingSession_Id(sessionId))
        .thenReturn(Optional.of(stagedFace(session, 90.0)));
    when(stagingLivenessResultRepository.findByOnboardingSession_Id(sessionId))
        .thenReturn(Optional.of(stagedLiveness(session)));

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
    when(stagingDocumentRepository.findByOnboardingSession_Id(sessionId))
        .thenReturn(stagedDocuments(session));
    when(stagingOcrResultRepository.findByOnboardingSession_Id(sessionId))
        .thenReturn(Optional.of(stagedOcr(session, 50.0)));
    when(stagingFaceVerificationResultRepository.findByOnboardingSession_Id(sessionId))
        .thenReturn(Optional.of(stagedFace(session, 90.0)));
    when(stagingLivenessResultRepository.findByOnboardingSession_Id(sessionId))
        .thenReturn(Optional.of(stagedLiveness(session)));

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
