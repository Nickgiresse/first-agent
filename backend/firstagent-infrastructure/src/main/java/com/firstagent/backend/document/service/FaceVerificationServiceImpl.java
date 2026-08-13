package com.firstagent.backend.document.service;

import com.firstagent.backend.audit.model.EvenementAudit;
import com.firstagent.backend.audit.model.TypeActeur;
import com.firstagent.backend.audit.port.JournalAudit;
import com.firstagent.backend.common.enums.DocumentType;
import com.firstagent.backend.common.enums.FaceVerificationStatus;
import com.firstagent.backend.common.exception.BusinessException;
import com.firstagent.backend.common.exception.ResourceNotFoundException;
import com.firstagent.backend.document.dto.FaceVerificationResponse;
import com.firstagent.backend.document.entity.StagingDocument;
import com.firstagent.backend.document.entity.StagingFaceVerificationResult;
import com.firstagent.backend.document.model.FaceMatchResult;
import com.firstagent.backend.document.port.FaceMatchProvider;
import com.firstagent.backend.document.repository.StagingDocumentRepository;
import com.firstagent.backend.document.repository.StagingFaceVerificationResultRepository;
import com.firstagent.backend.liveness.repository.StagingLivenessResultRepository;
import com.firstagent.backend.onboarding.entity.OnboardingSession;
import com.firstagent.backend.onboarding.service.OnboardingSessionService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FaceVerificationServiceImpl implements FaceVerificationService {

  private final StagingDocumentRepository stagingDocumentRepository;
  private final StagingFaceVerificationResultRepository stagingFaceVerificationResultRepository;
  private final StagingLivenessResultRepository stagingLivenessResultRepository;
  private final OnboardingSessionService onboardingSessionService;
  private final JournalAudit journal;
  private final StorageService storageService;
  private final FaceMatchProvider faceMatchProvider;

  /**
   * Que faire d'un selfie qu'on n'a pas pu rattacher au visage du défi.
   *
   * <p>{@code strict} (défaut) refuse. C'est tenable parce que le parcours appelle la vérification
   * faciale immédiatement après le défi, dans le même écran : la session de vivacité a quelques
   * secondes, très loin de ses cinq minutes de validité. Un rattachement impossible signale donc
   * une anomalie, pas un cas d'usage normal.
   *
   * <p>{@code off} journalise sans refuser. Prévu comme issue de secours si la mesure devait se
   * révéler trop stricte en production, pas comme un réglage ordinaire : il rouvre exactement la
   * faille que cette liaison ferme.
   */
  @Value("${app.identity.liveness-binding-mode:strict}")
  private String modeLiaisonVivacite;

  @Override
  @Transactional(noRollbackFor = BusinessException.class)
  public FaceVerificationResponse verifyFace(String sessionToken) {
    OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);

    StagingDocument cniPhoto =
        stagingDocumentRepository
            .findByOnboardingSession_IdAndDocumentType(session.getId(), DocumentType.CNI_RECTO)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "Le recto de la CNI doit être téléversé avant la vérification faciale"));
    StagingDocument selfie =
        stagingDocumentRepository
            .findByOnboardingSession_IdAndDocumentType(session.getId(), DocumentType.SELFIE)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "Le selfie doit être téléversé avant la vérification faciale"));

    // Identifiant du défi joué par ce client. C'est lui qui rattache la comparaison à une
    // personne : sans lui, « la vivacité est prouvée » et « le selfie correspond à la pièce »
    // sont deux faits vrais séparément, qui peuvent concerner deux individus différents.
    String livenessSessionId =
        stagingLivenessResultRepository
            .findByOnboardingSession_Id(session.getId())
            .map(resultat -> resultat.getSessionId())
            .orElse(null);

    byte[] cniPhotoBytes = storageService.read(cniPhoto.getFilePath());
    byte[] selfieBytes = storageService.read(selfie.getFilePath());

    FaceMatchResult matchResult =
        faceMatchProvider.compareFaces(cniPhotoBytes, selfieBytes, livenessSessionId);

    StagingFaceVerificationResult result =
        stagingFaceVerificationResultRepository
            .findByOnboardingSession_Id(session.getId())
            .orElseGet(
                () -> StagingFaceVerificationResult.builder().onboardingSession(session).build());

    result.setSimilarityScore(matchResult.similarityScore());
    result.setTargetQualityScore(matchResult.targetQualityScore());
    result.setProvider(faceMatchProvider.getProviderName());
    result.setStatus(
        matchResult.matched() ? FaceVerificationStatus.VERIFIED : FaceVerificationStatus.FAILED);
    result.setVerifiedAt(LocalDateTime.now());

    FaceVerificationResponse response =
        mapToResponse(stagingFaceVerificationResultRepository.save(result));

    if (!matchResult.matched()) {
      // Un refus isolé est banal : mauvais éclairage, visage partiellement
      // masqué. Leur répétition sur un même compte l'est moins, et c'est ce
      // que les règles d'alerte cherchent.
      journal.enregistrer(
          JournalAudit.EcritureAudit.echec(
              session.getPhoneNumber(),
              EvenementAudit.FACIAL_REFUSE,
              session.identifiantActeur(),
              TypeActeur.CLIENT,
              String.format(
                  "Comparaison faciale sous le seuil (similarité %.3f, qualité du selfie %.3f)",
                  matchResult.similarityScore(), matchResult.targetQualityScore())));

      throw new BusinessException(
          "Le visage sur le selfie ne correspond pas à la photo de la CNI. Reprenez la photo dans de meilleures conditions (visage bien visible, sans lunettes de soleil ni masque).");
    }

    verifierLeRattachementAuDefi(session, matchResult, livenessSessionId);

    return response;
  }

  /**
   * Refuse un selfie qui n'a pas pu être rattaché au visage ayant joué le défi.
   *
   * <p>Le cas du selfie rattaché à quelqu'un d'AUTRE est déjà traité plus haut : le microservice
   * ramène alors la décision à NO_MATCH et la méthode a déjà levé. Ce qui reste ici est l'absence
   * de rattachement, et elle mérite un traitement propre parce qu'elle est atteignable
   * volontairement : il suffit de jouer le défi, d'attendre l'expiration de la session côté
   * microservice, puis de soumettre le selfie d'une autre personne. Le résultat de vivacité
   * enregistré dirait toujours LIVE, et plus rien ne relierait ce LIVE au selfie comparé.
   */
  private void verifierLeRattachementAuDefi(
      OnboardingSession session, FaceMatchResult matchResult, String livenessSessionId) {
    if (Boolean.TRUE.equals(matchResult.livenessBound())) {
      return;
    }

    String detail =
        livenessSessionId == null
            ? "Vérification faciale demandée sans défi de vivacité préalable"
            : "Selfie non rattaché au visage du défi de vivacité (session "
                + livenessSessionId
                + ")";

    journal.enregistrer(
        JournalAudit.EcritureAudit.echec(
            session.getPhoneNumber(),
            EvenementAudit.FACIAL_REFUSE,
            session.identifiantActeur(),
            TypeActeur.CLIENT,
            detail));

    if ("off".equalsIgnoreCase(modeLiaisonVivacite)) {
      return;
    }

    throw new BusinessException(
        "Nous n'avons pas pu établir que le selfie est bien celui de la personne ayant réalisé "
            + "le test de vivacité. Reprenez le test, une seule personne devant la caméra.");
  }

  @Override
  public FaceVerificationResponse getVerification(String sessionToken) {
    OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);
    return mapToResponse(
        stagingFaceVerificationResultRepository
            .findByOnboardingSession_Id(session.getId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Aucune vérification faciale trouvée pour ce client")));
  }

  @Override
  public boolean isVerified(String sessionToken) {
    OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);
    return stagingFaceVerificationResultRepository.existsByOnboardingSession_IdAndStatus(
        session.getId(), FaceVerificationStatus.VERIFIED);
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
