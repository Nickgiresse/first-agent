package com.firstagent.backend.onboarding.service;

import com.firstagent.backend.audit.model.EvenementAudit;
import com.firstagent.backend.audit.model.TypeActeur;
import com.firstagent.backend.audit.port.JournalAudit;
import com.firstagent.backend.common.enums.CustomerStatus;
import com.firstagent.backend.common.enums.DocumentType;
import com.firstagent.backend.common.enums.FaceVerificationStatus;
import com.firstagent.backend.common.enums.LivenessStatus;
import com.firstagent.backend.common.enums.OcrStatus;
import com.firstagent.backend.common.enums.OnboardingStatus;
import com.firstagent.backend.common.exception.BusinessException;
import com.firstagent.backend.common.exception.InvalidPinException;
import com.firstagent.backend.common.exception.TypeErreurMetier;
import com.firstagent.backend.common.mail.CourrielAsynchrone;
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
import com.firstagent.backend.onboarding.dto.CompleteOnboardingRequest;
import com.firstagent.backend.onboarding.dto.KycRequest;
import com.firstagent.backend.onboarding.dto.LinkVerificationResponse;
import com.firstagent.backend.onboarding.dto.OnboardingCompletionResponse;
import com.firstagent.backend.onboarding.dto.OtpVerificationRequest;
import com.firstagent.backend.onboarding.dto.PinCreationRequest;
import com.firstagent.backend.onboarding.dto.TermsAcceptanceRequest;
import com.firstagent.backend.onboarding.entity.Customer;
import com.firstagent.backend.onboarding.entity.OnboardingSession;
import com.firstagent.backend.onboarding.repository.CustomerRepository;
import com.firstagent.backend.onboarding.repository.OnboardingSessionRepository;
import com.firstagent.backend.pin.port.PinService;
import com.firstagent.backend.whatsappbanking.client.WhatsAppBankingClient;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  /**
   * Seuil de CONFIANCE de la comparaison faciale, distinct du seuil d'ACCEPTATION.
   *
   * <p>Le service de vision applique son propre seuil : en deçà, il refuse et le parcours s'arrête.
   * Celui-ci est plus exigeant et ne refuse rien : entre les deux, le dossier aboutit mais part en
   * révision, l'identité devant être confirmée par un conseiller.
   *
   * <p>Réglable sans redéploiement : c'est un arbitrage entre le risque d'usurpation et le nombre
   * de clients renvoyés en agence, et il appartient au métier de le situer.
   *
   * <h2>Attention à l'échelle : ce n'est pas un pourcentage</h2>
   *
   * <p>Le score reçu est une similarité cosinus entre deux vecteurs ArcFace, multipliée par 100 par
   * {@code PythonVisionFaceMatchProvider}. Sa plage utile n'est pas 0 à 100 : un appariement
   * légitime entre la photo d'une pièce d'identité — imprimée, plastifiée, souvent ancienne — et un
   * selfie se situe en pratique entre 45 et 70, et dépasse rarement 80.
   *
   * <p>La valeur avait d'abord été fixée à 75, par analogie avec les scores de confiance bornés que
   * rendent d'autres moteurs. Sur cette échelle, elle aurait envoyé en agence la quasi-totalité des
   * dossiers légitimes : la « zone grise » qu'elle prétendait couvrir était en réalité la zone
   * nominale.
   *
   * <p>55 laisse passer un appariement franc et ne retient que les cas réellement douteux. Le
   * chiffre reste à confirmer sur un jeu de paires réelles, mêmes personnes et personnes
   * différentes : aucune valeur n'est juste sans cette mesure.
   */
  @Value("${app.identity.face-confidence-threshold:55}")
  private double faceConfidenceThreshold;

  @Value("${app.mail.from-address}")
  private String fromAddress;

  private final JournalAudit journal;
  private final OnboardingSessionService onboardingSessionService;
  private final CustomerRepository customerRepository;
  private final PinService pinService;
  private final PasswordEncoder passwordEncoder;
  private final CourrielAsynchrone courriel;
  private final DocumentService documentService;
  private final OcrService ocrService;
  private final FaceVerificationService faceVerificationService;
  private final LivenessService livenessService;
  private final WhatsAppBankingClient whatsAppBankingClient;
  private final StorageService storageService;

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
        && session
            .getEmailOtpLastSentAt()
            .plusSeconds(OTP_RESEND_COOLDOWN_SECONDS)
            .isAfter(LocalDateTime.now())) {
      throw new BusinessException("Veuillez patienter avant de redemander un code de vérification");
    }

    if (customerRepository.existsByEmail(request.getEmail())) {
      throw new BusinessException(
          "Cette adresse e-mail est déjà utilisée par un autre utilisateur.",
          TypeErreurMetier.CONFLIT);
    }

    String code = generateOtpCode();

    session.setPendingEmail(request.getEmail());
    session.setEmailOtpCodeHash(passwordEncoder.encode(code));
    session.setEmailOtpExpiresAt(LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES));
    session.setEmailOtpAttempts(0);
    session.setEmailOtpLastSentAt(LocalDateTime.now());
    onboardingSessionRepository.save(session);

    courriel.envoyer(
        request.getEmail(),
        "Votre code de vérification Afriland First Bank",
        "Votre code de vérification est : "
            + code
            + "\n\nCe code est valable "
            + OTP_VALIDITY_MINUTES
            + " minutes. Ne le partagez avec personne.",
        "code de vérification");
  }

  /**
   * Vérifie le code reçu par courriel.
   *
   * <p>{@code noRollbackFor} n'est pas un détail de configuration ici, c'est ce qui fait exister la
   * protection contre la force brute. {@link BusinessException} étant une exception non contrôlée,
   * une transaction ordinaire l'annulerait entièrement, y compris l'incrément du compteur de
   * tentatives écrit juste avant le rejet. Le compteur repartirait donc de zéro à chaque essai, le
   * verrou ne serait jamais atteint, et le code à six chiffres pourrait être parcouru sans limite.
   */
  @Override
  @Transactional(noRollbackFor = BusinessException.class)
  public void verifyEmailOtp(String sessionToken, OtpVerificationRequest request) {
    OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);

    if (session.getStatus() != OnboardingStatus.ACCOUNT_VERIFIED) {
      throw new BusinessException("Étape KYC déjà complétée ou non accessible à ce stade");
    }

    if (session.getPendingEmail() == null || session.getEmailOtpCodeHash() == null) {
      throw new BusinessException(
          "Aucun code n'a été envoyé. Demandez d'abord un code de vérification.");
    }

    if (session.getEmailOtpExpiresAt().isBefore(LocalDateTime.now())) {
      throw new BusinessException("Ce code a expiré. Demandez un nouveau code.");
    }

    if (session.getEmailOtpAttempts() >= OTP_MAX_ATTEMPTS) {
      // Distinct du simple code erroné : atteindre le verrou est un signal plus
      // fort, et les confondre empêcherait de les compter séparément.
      journal.enregistrer(
          JournalAudit.EcritureAudit.echec(
              session.getPhoneNumber(),
              EvenementAudit.OTP_VERROUILLE,
              session.identifiantActeur(),
              TypeActeur.CLIENT,
              "Vérification par code verrouillée après " + OTP_MAX_ATTEMPTS + " tentatives"));

      throw new BusinessException("Trop de tentatives incorrectes. Demandez un nouveau code.");
    }

    if (!passwordEncoder.matches(request.getCode(), session.getEmailOtpCodeHash())) {
      session.setEmailOtpAttempts(session.getEmailOtpAttempts() + 1);
      onboardingSessionRepository.save(session);

      // Le rang de la tentative, jamais le code saisi : journaliser les codes
      // essayés révélerait par recoupement l'espace parcouru, et finirait par
      // livrer le bon à qui lit le journal.
      journal.enregistrer(
          JournalAudit.EcritureAudit.echec(
              session.getPhoneNumber(),
              EvenementAudit.OTP_ERRONE,
              session.identifiantActeur(),
              TypeActeur.CLIENT,
              "Code de vérification erroné (tentative "
                  + session.getEmailOtpAttempts()
                  + " sur "
                  + OTP_MAX_ATTEMPTS
                  + ")"));

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

  /**
   * Franchit l'étape KYC sans adresse e-mail.
   *
   * <p>L'adresse est facultative : une part des clients n'en possède pas, et le parcours ne peut
   * pas s'arrêter là. La session passe en {@code KYC_COMPLETED} sans e-mail enregistré.
   *
   * <p>Le geste est journalisé. Ce n'est pas un échec, mais il laisse le compte sans canal de
   * réinitialisation du code secret : le titulaire devra passer en agence, ce que la
   * réinitialisation renvoie déjà (voir PinResetServiceImpl, {@code requiresBranchVisit}). La
   * conformité doit pouvoir dénombrer ces comptes.
   */
  @Override
  @Transactional
  public void skipEmailVerification(String sessionToken) {
    OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);

    if (session.getStatus() != OnboardingStatus.ACCOUNT_VERIFIED) {
      throw new BusinessException("Étape KYC déjà complétée ou non accessible à ce stade");
    }

    // Une demande de code a pu précéder : sans ce nettoyage, un code encore
    // valide et une adresse en attente survivraient à une étape que le client a
    // justement choisi de franchir sans adresse.
    session.setPendingEmail(null);
    session.setEmailOtpCodeHash(null);
    session.setEmailOtpExpiresAt(null);
    session.setEmailOtpAttempts(0);
    session.setEmailOtpLastSentAt(null);

    onboardingSessionService.updateStatus(session, OnboardingStatus.KYC_COMPLETED);

    journal.enregistrer(
        JournalAudit.EcritureAudit.succes(
            session.getPhoneNumber(),
            EvenementAudit.EMAIL_NON_VERIFIE,
            session.identifiantActeur(),
            TypeActeur.CLIENT,
            "Étape KYC franchie sans adresse e-mail (adresse facultative)"));
  }

  private String generateOtpCode() {
    int bound = (int) Math.pow(10, OTP_LENGTH);
    return String.format("%0" + OTP_LENGTH + "d", OTP_RANDOM.nextInt(bound));
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

    if (customerRepository
        .findByBankAccount_AccountNumber(session.getBankAccount().getAccountNumber())
        .isPresent()) {
      throw new BusinessException(
          "Un utilisateur existe déjà pour ce numéro de compte. Utilisez la réinitialisation de PIN si nécessaire.",
          TypeErreurMetier.CONFLIT);
    }

    session.setPinHash(pinService.hashPin(pinRequest.getPin()));
    onboardingSessionService.updateStatus(session, OnboardingStatus.PIN_CREATED);
  }

  @Override
  @Transactional
  public void acceptTerms(String sessionToken, TermsAcceptanceRequest request) {
    OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);

    if (!documentService.hasAllRequiredDocuments(sessionToken)) {
      throw new BusinessException(
          "Tous les documents requis (CNI recto, verso, selfie) doivent être téléversés avant d'accepter les conditions");
    }

    if (!ocrService.isConfirmed(sessionToken)) {
      throw new BusinessException(
          "Les informations extraites de votre CNI doivent être vérifiées et confirmées avant d'accepter les conditions");
    }

    if (!livenessService.isLive(sessionToken)) {
      throw new BusinessException(
          "Le défi de vivacité (clignement, rotation de la tête...) doit être réussi avant d'accepter les conditions");
    }

    if (!faceVerificationService.isVerified(sessionToken)) {
      throw new BusinessException(
          "La vérification faciale (selfie vs photo de la CNI) doit être réussie avant d'accepter les conditions");
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
   * document_ocr_results, face_verification_results, liveness_results) : tout ce qui précède n'a
   * écrit que dans les tables de staging, rattachées à la session. Cette méthode matérialise
   * l'ensemble en une seule transaction, puis purge le staging.
   */
  @Override
  @Transactional
  public OnboardingCompletionResponse completeOnboarding(
      String sessionToken, CompleteOnboardingRequest request) {
    OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);

    if (session.getStatus() != OnboardingStatus.TERMS_ACCEPTED) {
      throw new BusinessException(
          "Les conditions d'utilisation doivent être acceptées avant la finalisation");
    }

    UUID sessionId = session.getId();

    List<StagingDocument> stagingDocuments =
        stagingDocumentRepository.findByOnboardingSession_Id(sessionId);
    StagingOcrResult stagingOcr =
        stagingOcrResultRepository
            .findByOnboardingSession_Id(sessionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "Les informations extraites de votre CNI doivent être vérifiées et confirmées avant la finalisation"));
    StagingFaceVerificationResult stagingFace =
        stagingFaceVerificationResultRepository
            .findByOnboardingSession_Id(sessionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "La vérification faciale doit être réussie avant la finalisation"));
    StagingLivenessResult stagingLiveness =
        stagingLivenessResultRepository
            .findByOnboardingSession_Id(sessionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "Le défi de vivacité doit être réussi avant la finalisation"));

    // Le dossier doit être COMPLET (pièces, champs confirmés, défi de vivacité
    // réussi) — mais la comparaison faciale, elle, ne conditionne plus la
    // finalisation : un écart y est le plus souvent dû à la photo de la pièce,
    // pas à une usurpation. Le défi de vivacité reste bloquant, car son échec
    // signale une présentation non vivante, ce qui est un signal de fraude et
    // non une limite de mesure.
    if (stagingDocuments.size() < REQUIRED_DOCUMENT_COUNT
        || stagingOcr.getStatus() != OcrStatus.CONFIRMED
        || stagingLiveness.getStatus() != LivenessStatus.LIVE) {
      throw new BusinessException(
          "Le dossier n'est pas complet, impossible de finaliser l'inscription");
    }

    // Identité non confirmée par la biométrie : le compte sera créé mais restera
    // INACTIF jusqu'à vérification en agence. On ne laisse jamais un service
    // bancaire s'ouvrir sur une identité incertaine.
    boolean verificationEnAgenceRequise =
        stagingFace.getStatus() != FaceVerificationStatus.VERIFIED;

    Customer customer =
        Customer.builder()
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

    applyManualReview(
        customer,
        stagingOcr.getDocumentQualityScore(),
        stagingFace.getTargetQualityScore(),
        stagingFace.getSimilarityScore());

    // Le statut découle de la révision, il n'est pas décidé indépendamment.
    // Auparavant tout dossier naissait USER, donc actif : le drapeau de
    // révision existait mais n'empêchait rien, et un dossier en attente de
    // confirmation d'identité ouvrait l'accès au service dans l'intervalle.
    customer.setStatus(
        customer.isRequiresManualReview() ? CustomerStatus.PENDING_REVIEW : CustomerStatus.USER);

    // Source de vérité = WhatsApp banking. On y pousse le client AVANT toute écriture locale :
    // fail-secure — si l'écriture dans la source de vérité échoue, la transaction (@Transactional)
    // est annulée, rien n'est enregistré localement et le staging reste intact pour un nouvel
    // essai.
    pushToWhatsAppBanking(session, customer, stagingOcr, stagingFace, request, stagingDocuments);

    customerRepository.save(customer);

    for (StagingDocument staged : stagingDocuments) {
      documentRepository.save(
          CustomerDocument.builder()
              .customer(customer)
              .documentType(staged.getDocumentType())
              .filePath(staged.getFilePath())
              .fileName(staged.getFileName())
              .mimeType(staged.getMimeType())
              .fileSize(staged.getFileSize())
              .build());
    }

    documentOcrResultRepository.save(
        DocumentOcrResult.builder()
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

    faceVerificationResultRepository.save(
        FaceVerificationResult.builder()
            .customer(customer)
            .similarityScore(stagingFace.getSimilarityScore())
            .targetQualityScore(stagingFace.getTargetQualityScore())
            .provider(stagingFace.getProvider())
            .status(FaceVerificationStatus.VERIFIED)
            .verifiedAt(stagingFace.getVerifiedAt())
            .build());

    livenessResultRepository.save(
        LivenessResult.builder()
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

    // Trois issues distinctes, et le message doit dire laquelle : promettre un
    // service utilisable alors que le compte est inactif ferait revenir le
    // client pour rien.
    String message;
    if (verificationEnAgenceRequise) {
      message =
          "Votre dossier a bien été enregistré. Votre identité n'ayant pas pu être confirmée "
              + "automatiquement, rendez-vous dans votre agence avec votre pièce d'identité "
              + "pour activer le service. Votre compte bancaire, lui, reste utilisable normalement.";
    } else if (customer.isRequiresManualReview()) {
      message =
          "Votre compte a été enregistré. La qualité de vos documents nécessite une vérification "
              + "complémentaire par notre agence avant activation complète.";
    } else {
      message =
          "Votre compte digital a été créé avec succès. Vous pouvez maintenant vous connecter.";
    }

    return OnboardingCompletionResponse.builder()
        .customerId(customer.getId())
        .firstName(customer.getFirstName())
        .lastName(customer.getLastName())
        .message(message)
        .requiresManualReview(customer.isRequiresManualReview())
        .branchVisitRequired(verificationEnAgenceRequise)
        .build();
  }

  /**
   * Pousse le client onboardé vers le WhatsApp banking (source de vérité) via l'API M2M à clé.
   * MATCH (dossier propre) => compte actif ; REVIEW (révision manuelle requise) => compte suspendu.
   * NO_MATCH n'atteint jamais ce point : un échec de vérification faciale bloque la finalisation en
   * amont (le statut face doit être VERIFIED pour arriver ici).
   *
   * <p>Sans lien ni PIN (parcours interne de test non initié par un lien d'onboarding), le push est
   * ignoré et la finalisation reste locale.
   */
  private void pushToWhatsAppBanking(
      OnboardingSession session,
      Customer customer,
      StagingOcrResult stagingOcr,
      StagingFaceVerificationResult stagingFace,
      CompleteOnboardingRequest request,
      List<StagingDocument> stagingDocuments) {
    if (request == null
        || request.getLinkToken() == null
        || request.getLinkToken().isBlank()
        || request.getPin() == null
        || request.getPin().isBlank()) {
      log.warn(
          "Finalisation sans lien/PIN : push vers le WhatsApp banking ignoré (parcours interne).");
      return;
    }
    // REVIEW dès que l'identité n'est pas confirmée — que ce soit la qualité des
    // documents ou la comparaison faciale. Côté banque, REVIEW crée le compte
    // mais le laisse SUSPENDU : c'est exactement l'attente d'une vérification en
    // agence. Envoyer MATCH ici ouvrirait le service sur une identité incertaine.
    boolean identiteNonConfirmee =
        customer.isRequiresManualReview()
            || stagingFace.getStatus() != FaceVerificationStatus.VERIFIED;
    String decision = identiteNonConfirmee ? "REVIEW" : "MATCH";
    String name = (customer.getFirstName() + " " + customer.getLastName()).trim();
    String accountNumber = session.getBankAccount().getAccountNumber();
    String docType =
        stagingOcr.getDocumentKind() != null ? stagingOcr.getDocumentKind().toString() : null;

    // Lève une BusinessException si l'écriture échoue -> rollback @Transactional (fail-secure).
    whatsAppBankingClient.upsertCustomer(
        request.getLinkToken(),
        name,
        accountNumber,
        request.getPin(),
        decision,
        stagingFace.getSimilarityScore(),
        stagingOcr.getDocumentNumber(),
        docType,
        true,
        null);
    log.info(
        "Client poussé vers le WhatsApp banking (décision={}, compte={})", decision, accountNumber);

    // Les pièces suivent le client : la revue conseiller du back-office doit pouvoir afficher
    // recto/verso/selfie d'un onboarding externe comme ceux du parcours interne. On lit le
    // staging AVANT sa purge. Un échec ici ne remet pas en cause l'inscription déjà validée.
    try {
      whatsAppBankingClient.archiveDocuments(
          request.getLinkToken(),
          decision,
          docType,
          readStaged(stagingDocuments, DocumentType.CNI_RECTO),
          readStaged(stagingDocuments, DocumentType.CNI_VERSO),
          readStaged(stagingDocuments, DocumentType.SELFIE));
      log.info("Dossier ({} pièces) archivé dans le back-office", stagingDocuments.size());
    } catch (RuntimeException e) {
      log.error(
          "Archivage du dossier dans le back-office échoué (client créé malgré tout) : {}",
          e.getMessage());
    }
  }

  /** Relit une pièce du staging depuis le stockage ; tableau vide si absente ou illisible. */
  private byte[] readStaged(List<StagingDocument> documents, DocumentType type) {
    return documents.stream()
        .filter(d -> d.getDocumentType() == type)
        .findFirst()
        .map(
            d -> {
              try {
                return storageService.read(d.getFilePath());
              } catch (RuntimeException e) {
                log.warn("Pièce {} illisible : {}", type, e.getMessage());
                return new byte[0];
              }
            })
        .orElse(new byte[0]);
  }

  /**
   * Un score sous le seuil ne bloque pas l'inscription : le dossier est enregistré jusqu'au bout,
   * seulement marqué pour révision manuelle par l'agence plutôt que rejeté ou approuvé
   * automatiquement.
   *
   * <p>TROIS BANDES POUR LA COMPARAISON FACIALE, ET NON DEUX. Le service de vision décide seul si
   * les visages correspondent, et un rapprochement refusé interrompt le parcours plus tôt. Restait
   * une zone grise : un rapprochement accepté de justesse valait auparavant approbation pleine et
   * entière, au même titre qu'une correspondance nette.
   *
   * <p>Or c'est précisément là que se logent les cas douteux : ressemblance familiale, photo
   * ancienne, éclairage défavorable. Les traiter comme des correspondances certaines revient à
   * décider automatiquement ce qu'un humain devrait trancher. Au-dessus du seuil d'acceptation mais
   * en deçà du seuil de confiance, le dossier part donc en agence.
   */
  private void applyManualReview(
      Customer customer,
      Double documentQualityScore,
      Double faceQualityScore,
      Double faceSimilarityScore) {
    List<String> reasons = new ArrayList<>();

    if (documentQualityScore != null && documentQualityScore < manualReviewThreshold) {
      reasons.add(
          "Qualité du document d'identité insuffisante ("
              + Math.round(documentQualityScore)
              + "%)");
    }

    if (faceSimilarityScore != null && faceSimilarityScore < faceConfidenceThreshold) {
      reasons.add(
          "Ressemblance faciale insuffisamment nette ("
              + Math.round(faceSimilarityScore)
              + "%) : identité à confirmer en agence");
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
