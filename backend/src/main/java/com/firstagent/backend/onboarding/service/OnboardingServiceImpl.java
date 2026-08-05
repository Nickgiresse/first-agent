package com.firstagent.backend.onboarding.service;

import com.firstagent.backend.common.enums.CustomerStatus;
import com.firstagent.backend.common.enums.FaceVerificationStatus;
import com.firstagent.backend.common.enums.LivenessStatus;
import com.firstagent.backend.common.enums.OcrStatus;
import com.firstagent.backend.common.enums.OnboardingStatus;
import com.firstagent.backend.common.exception.BusinessException;
import com.firstagent.backend.common.exception.InvalidPinException;
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
import com.firstagent.backend.onboarding.dto.*;
import com.firstagent.backend.whatsappbanking.client.WhatsAppBankingClient;
import lombok.extern.slf4j.Slf4j;
import com.firstagent.backend.onboarding.entity.Customer;
import com.firstagent.backend.onboarding.entity.OnboardingSession;
import com.firstagent.backend.onboarding.repository.CustomerRepository;
import com.firstagent.backend.onboarding.repository.OnboardingSessionRepository;
import com.firstagent.backend.pin.service.PinService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OnboardingServiceImpl implements OnboardingService {

    private static final int REQUIRED_DOCUMENT_COUNT = 3;
    private static final int OTP_LENGTH = 6;
    private static final int OTP_VALIDITY_MINUTES = 10;
    private static final int OTP_MAX_ATTEMPTS = 5;
    private static final int OTP_RESEND_COOLDOWN_SECONDS = 45;

    private static final SecureRandom OTP_RANDOM = new SecureRandom();

    // En dessous de ce score qualité (document OU selfie), le dossier n'est pas rejeté : il est
    // enregistré jusqu'au bout mais marqué pour révision manuelle par l'agence.
    @Value("${app.quality.manual-review-threshold:70}")
    private double manualReviewThreshold;

    @Value("${app.mail.from-address}")
    private String fromAddress;

    private final OnboardingSessionService onboardingSessionService;
    private final CustomerRepository customerRepository;
    private final PinService pinService;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final DocumentService documentService;
    private final OcrService ocrService;
    private final FaceVerificationService faceVerificationService;
    private final LivenessService livenessService;
    private final WhatsAppBankingClient whatsAppBankingClient;

    private final StagingDocumentRepository stagingDocumentRepository;
    private final StagingOcrResultRepository stagingOcrResultRepository;
    private final StagingFaceVerificationResultRepository stagingFaceVerificationResultRepository;
    private final StagingLivenessResultRepository stagingLivenessResultRepository;

    private final DocumentRepository documentRepository;
    private final DocumentOcrResultRepository documentOcrResultRepository;
    private final FaceVerificationResultRepository faceVerificationResultRepository;
    private final LivenessResultRepository livenessResultRepository;
    private final OnboardingSessionRepository onboardingSessionRepository;

    @Override
    @Transactional
    public void requestEmailOtp(String sessionToken, KycRequest request) {
        OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);

        if (session.getStatus() != OnboardingStatus.ACCOUNT_VERIFIED) {
            throw new BusinessException("Étape KYC déjà complétée ou non accessible à ce stade");
        }

        if (session.getEmailOtpLastSentAt() != null
            && session.getEmailOtpLastSentAt().plusSeconds(OTP_RESEND_COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
            throw new BusinessException("Veuillez patienter avant de redemander un code de vérification");
        }

        if (customerRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Cette adresse e-mail est déjà utilisée par un autre utilisateur.", HttpStatus.CONFLICT);
        }

        String code = generateOtpCode();

        session.setPendingEmail(request.getEmail());
        session.setEmailOtpCodeHash(passwordEncoder.encode(code));
        session.setEmailOtpExpiresAt(LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES));
        session.setEmailOtpAttempts(0);
        session.setEmailOtpLastSentAt(LocalDateTime.now());
        onboardingSessionRepository.save(session);

        sendOtpEmail(request.getEmail(), code);
    }

    @Override
    @Transactional
    public void verifyEmailOtp(String sessionToken, OtpVerificationRequest request) {
        OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);

        if (session.getStatus() != OnboardingStatus.ACCOUNT_VERIFIED) {
            throw new BusinessException("Étape KYC déjà complétée ou non accessible à ce stade");
        }

        if (session.getPendingEmail() == null || session.getEmailOtpCodeHash() == null) {
            throw new BusinessException("Aucun code n'a été envoyé. Demandez d'abord un code de vérification.");
        }

        if (session.getEmailOtpExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Ce code a expiré. Demandez un nouveau code.");
        }

        if (session.getEmailOtpAttempts() >= OTP_MAX_ATTEMPTS) {
            throw new BusinessException("Trop de tentatives incorrectes. Demandez un nouveau code.");
        }

        if (!passwordEncoder.matches(request.getCode(), session.getEmailOtpCodeHash())) {
            session.setEmailOtpAttempts(session.getEmailOtpAttempts() + 1);
            onboardingSessionRepository.save(session);
            throw new BusinessException("Code de vérification incorrect");
        }

        session.setEmail(session.getPendingEmail());
        session.setPendingEmail(null);
        session.setEmailOtpCodeHash(null);
        session.setEmailOtpExpiresAt(null);
        session.setEmailOtpAttempts(0);
        session.setEmailOtpLastSentAt(null);
        onboardingSessionService.updateStatus(session, OnboardingStatus.KYC_COMPLETED);
    }

    private String generateOtpCode() {
        int bound = (int) Math.pow(10, OTP_LENGTH);
        return String.format("%0" + OTP_LENGTH + "d", OTP_RANDOM.nextInt(bound));
    }

    private void sendOtpEmail(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Votre code de vérification Afriland First Bank");
        message.setText("Votre code de vérification est : " + code + "\n\nCe code est valable "
            + OTP_VALIDITY_MINUTES + " minutes. Ne le partagez avec personne.");
        mailSender.send(message);
    }

    @Override
    @Transactional
    public void createProfile(String sessionToken, PinCreationRequest pinRequest) {
        OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);

        if (session.getStatus() != OnboardingStatus.KYC_COMPLETED) {
            throw new BusinessException("Le KYC doit être complété avant la création du PIN");
        }

        if (!pinRequest.getPin().equals(pinRequest.getPinConfirmation())) {
            throw new InvalidPinException("Le PIN et sa confirmation ne correspondent pas");
        }

        if (customerRepository.findByBankAccount_AccountNumber(session.getBankAccount().getAccountNumber()).isPresent()) {
            throw new BusinessException("Un utilisateur existe déjà pour ce numéro de compte. Utilisez la réinitialisation de PIN si nécessaire.", HttpStatus.CONFLICT);
        }

        session.setPinHash(pinService.hashPin(pinRequest.getPin()));
        onboardingSessionService.updateStatus(session, OnboardingStatus.PIN_CREATED);
    }

    @Override
    @Transactional
    public void acceptTerms(String sessionToken, TermsAcceptanceRequest request) {
        OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);

        if (!documentService.hasAllRequiredDocuments(sessionToken)) {
            throw new BusinessException("Tous les documents requis (CNI recto, verso, selfie) doivent être téléversés avant d'accepter les conditions");
        }

        if (!ocrService.isConfirmed(sessionToken)) {
            throw new BusinessException("Les informations extraites de votre CNI doivent être vérifiées et confirmées avant d'accepter les conditions");
        }

        if (!livenessService.isLive(sessionToken)) {
            throw new BusinessException("Le défi de vivacité (clignement, rotation de la tête...) doit être réussi avant d'accepter les conditions");
        }

        if (!faceVerificationService.isVerified(sessionToken)) {
            throw new BusinessException("La vérification faciale (selfie vs photo de la CNI) doit être réussie avant d'accepter les conditions");
        }

        session.setTermsAccepted(true);
        session.setTermsAcceptedAt(LocalDateTime.now());
        onboardingSessionService.updateStatus(session, OnboardingStatus.TERMS_ACCEPTED);
    }

    /** Entrée par lien : valide le JWT (?t=) auprès du WhatsApp banking et renvoie le contexte. */
    @Override
    public LinkVerificationResponse verifyOnboardingLink(String token) {
        Map<String, Object> node = whatsAppBankingClient.verifyToken(token);
        if (node == null || !Boolean.TRUE.equals(node.get("valid"))) {
            throw new BusinessException("Lien invalide, expiré ou déjà utilisé.");
        }
        return LinkVerificationResponse.builder()
            .valid(true)
            .phone(asText(node.get("phone"), ""))
            .name(asText(node.get("name"), ""))
            .accountNumber(asText(node.get("account_number"), ""))
            .lang(asText(node.get("lang"), "fr"))
            .alreadyOnboarded(node.get("known_customer") != null)
            .build();
    }

    private static String asText(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    /**
     * Seul point d'écriture des tables définitives (customers, customer_documents,
     * document_ocr_results, face_verification_results, liveness_results) : tout ce qui précède
     * n'a écrit que dans les tables de staging, rattachées à la session. Cette méthode matérialise
     * l'ensemble en une seule transaction, puis purge le staging.
     */
    @Override
    @Transactional
    public OnboardingCompletionResponse completeOnboarding(String sessionToken, CompleteOnboardingRequest request) {
        OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);

        if (session.getStatus() != OnboardingStatus.TERMS_ACCEPTED) {
            throw new BusinessException("Les conditions d'utilisation doivent être acceptées avant la finalisation");
        }

        UUID sessionId = session.getId();

        List<StagingDocument> stagingDocuments = stagingDocumentRepository.findByOnboardingSession_Id(sessionId);
        StagingOcrResult stagingOcr = stagingOcrResultRepository.findByOnboardingSession_Id(sessionId)
            .orElseThrow(() -> new BusinessException("Les informations extraites de votre CNI doivent être vérifiées et confirmées avant la finalisation"));
        StagingFaceVerificationResult stagingFace = stagingFaceVerificationResultRepository.findByOnboardingSession_Id(sessionId)
            .orElseThrow(() -> new BusinessException("La vérification faciale doit être réussie avant la finalisation"));
        StagingLivenessResult stagingLiveness = stagingLivenessResultRepository.findByOnboardingSession_Id(sessionId)
            .orElseThrow(() -> new BusinessException("Le défi de vivacité doit être réussi avant la finalisation"));

        if (stagingDocuments.size() < REQUIRED_DOCUMENT_COUNT
            || stagingOcr.getStatus() != OcrStatus.CONFIRMED
            || stagingFace.getStatus() != FaceVerificationStatus.VERIFIED
            || stagingLiveness.getStatus() != LivenessStatus.LIVE) {
            throw new BusinessException("Le dossier n'est pas complet, impossible de finaliser l'inscription");
        }

        Customer customer = Customer.builder()
            .bankAccount(session.getBankAccount())
            .onboardingSession(session)
            .firstName(session.getBankAccount().getFirstName())
            .lastName(session.getBankAccount().getLastName())
            // Numéro déposé par le bot à l'ouverture du parcours et vérifié
            // contre le référentiel bancaire. C'est lui qui identifiera le
            // client sur WhatsApp : le laisser nul rendrait le compte
            // inutilisable par le bot, qui ne saurait plus à qui il parle.
            .phoneNumber(session.getPhoneNumber())
            .email(session.getEmail())
            .pinHash(session.getPinHash())
            .status(CustomerStatus.USER)
            .termsAccepted(true)
            .termsAcceptedAt(session.getTermsAcceptedAt())
            .build();

        applyManualReview(customer, stagingOcr.getDocumentQualityScore(), stagingFace.getTargetQualityScore());

        // Source de vérité = WhatsApp banking. On y pousse le client AVANT toute écriture locale :
        // fail-secure — si l'écriture dans la source de vérité échoue, la transaction (@Transactional)
        // est annulée, rien n'est enregistré localement et le staging reste intact pour un nouvel essai.
        pushToWhatsAppBanking(session, customer, stagingOcr, stagingFace, request);

        customerRepository.save(customer);

        for (StagingDocument staged : stagingDocuments) {
            documentRepository.save(CustomerDocument.builder()
                .customer(customer)
                .documentType(staged.getDocumentType())
                .filePath(staged.getFilePath())
                .fileName(staged.getFileName())
                .mimeType(staged.getMimeType())
                .fileSize(staged.getFileSize())
                .build());
        }

        documentOcrResultRepository.save(DocumentOcrResult.builder()
            .customer(customer)
            .documentKind(stagingOcr.getDocumentKind())
            .firstName(stagingOcr.getFirstName())
            .lastName(stagingOcr.getLastName())
            .documentNumber(stagingOcr.getDocumentNumber())
            .sex(stagingOcr.getSex())
            .birthDate(stagingOcr.getBirthDate())
            .expiryDate(stagingOcr.getExpiryDate())
            .birthPlace(stagingOcr.getBirthPlace())
            .fatherName(stagingOcr.getFatherName())
            .motherName(stagingOcr.getMotherName())
            .kitNumber(stagingOcr.getKitNumber())
            .requestIdentifier(stagingOcr.getRequestIdentifier())
            .ocrProvider(stagingOcr.getOcrProvider())
            .confidenceScore(stagingOcr.getConfidenceScore())
            .documentQualityScore(stagingOcr.getDocumentQualityScore())
            .status(OcrStatus.CONFIRMED)
            .extractedAt(stagingOcr.getExtractedAt())
            .confirmedAt(stagingOcr.getConfirmedAt())
            .build());

        faceVerificationResultRepository.save(FaceVerificationResult.builder()
            .customer(customer)
            .similarityScore(stagingFace.getSimilarityScore())
            .targetQualityScore(stagingFace.getTargetQualityScore())
            .provider(stagingFace.getProvider())
            .status(FaceVerificationStatus.VERIFIED)
            .verifiedAt(stagingFace.getVerifiedAt())
            .build());

        livenessResultRepository.save(LivenessResult.builder()
            .customer(customer)
            .sessionId(stagingLiveness.getSessionId())
            .status(LivenessStatus.LIVE)
            .completedActions(stagingLiveness.getCompletedActions())
            .totalActions(stagingLiveness.getTotalActions())
            .verifiedAt(stagingLiveness.getVerifiedAt())
            .build());

        stagingDocumentRepository.deleteByOnboardingSession_Id(sessionId);
        stagingOcrResultRepository.deleteByOnboardingSession_Id(sessionId);
        stagingFaceVerificationResultRepository.deleteByOnboardingSession_Id(sessionId);
        stagingLivenessResultRepository.deleteByOnboardingSession_Id(sessionId);

        onboardingSessionService.updateStatus(session, OnboardingStatus.COMPLETED);

        String message = customer.isRequiresManualReview()
            ? "Votre compte a été enregistré. La qualité de vos documents nécessite une vérification "
                + "complémentaire par notre agence avant activation complète."
            : "Votre compte digital a été créé avec succès. Vous pouvez maintenant vous connecter.";

        return OnboardingCompletionResponse.builder()
            .customerId(customer.getId())
            .firstName(customer.getFirstName())
            .lastName(customer.getLastName())
            .message(message)
            .requiresManualReview(customer.isRequiresManualReview())
            .build();
    }

    /**
     * Pousse le client onboardé vers le WhatsApp banking (source de vérité) via l'API M2M à clé.
     * MATCH (dossier propre) => compte actif ; REVIEW (révision manuelle requise) => compte suspendu.
     * NO_MATCH n'atteint jamais ce point : un échec de vérification faciale bloque la finalisation en
     * amont (le statut face doit être VERIFIED pour arriver ici).
     *
     * Sans lien ni PIN (parcours interne de test non initié par un lien d'onboarding), le push est
     * ignoré et la finalisation reste locale.
     */
    private void pushToWhatsAppBanking(
        OnboardingSession session, Customer customer,
        StagingOcrResult stagingOcr, StagingFaceVerificationResult stagingFace,
        CompleteOnboardingRequest request
    ) {
        if (request == null
            || request.getLinkToken() == null || request.getLinkToken().isBlank()
            || request.getPin() == null || request.getPin().isBlank()) {
            log.warn("Finalisation sans lien/PIN : push vers le WhatsApp banking ignoré (parcours interne).");
            return;
        }
        String decision = customer.isRequiresManualReview() ? "REVIEW" : "MATCH";
        String name = (customer.getFirstName() + " " + customer.getLastName()).trim();
        String accountNumber = session.getBankAccount().getAccountNumber();
        String docType = stagingOcr.getDocumentKind() != null ? stagingOcr.getDocumentKind().toString() : null;

        // Lève une BusinessException si l'écriture échoue -> rollback @Transactional (fail-secure).
        whatsAppBankingClient.upsertCustomer(
            request.getLinkToken(), name, accountNumber, request.getPin(),
            decision, stagingFace.getSimilarityScore(), stagingOcr.getDocumentNumber(), docType,
            true, null
        );
        log.info("Client poussé vers le WhatsApp banking (décision={}, compte={})", decision, accountNumber);
    }

    /**
     * Un score de qualité (document ou selfie) sous le seuil ne bloque pas l'inscription : le
     * dossier est enregistré jusqu'au bout, seulement marqué pour révision manuelle par l'agence
     * plutôt que rejeté ou approuvé automatiquement.
     */
    private void applyManualReview(Customer customer, Double documentQualityScore, Double faceQualityScore) {
        List<String> reasons = new ArrayList<>();

        if (documentQualityScore != null && documentQualityScore < manualReviewThreshold) {
            reasons.add("Qualité du document d'identité insuffisante (" + Math.round(documentQualityScore) + "%)");
        }

        if (faceQualityScore != null && faceQualityScore < manualReviewThreshold) {
            reasons.add("Qualité du selfie insuffisante (" + Math.round(faceQualityScore) + "%)");
        }

        if (!reasons.isEmpty()) {
            customer.setRequiresManualReview(true);
            customer.setManualReviewReason(String.join(" ; ", reasons));
        }
    }
}
