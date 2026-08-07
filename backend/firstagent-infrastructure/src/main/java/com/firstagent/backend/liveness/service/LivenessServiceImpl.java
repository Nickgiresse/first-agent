package com.firstagent.backend.liveness.service;

import com.firstagent.backend.audit.model.EvenementAudit;
import com.firstagent.backend.audit.model.TypeActeur;
import com.firstagent.backend.audit.port.JournalAudit;
import com.firstagent.backend.common.enums.LivenessStatus;
import com.firstagent.backend.common.exception.ResourceNotFoundException;
import com.firstagent.backend.liveness.dto.ChallengeStartResponse;
import com.firstagent.backend.liveness.dto.ChallengeStatusResponse;
import com.firstagent.backend.liveness.dto.ChallengeVerifyResponse;
import com.firstagent.backend.liveness.entity.StagingLivenessResult;
import com.firstagent.backend.liveness.repository.StagingLivenessResultRepository;
import com.firstagent.backend.onboarding.entity.OnboardingSession;
import com.firstagent.backend.onboarding.service.OnboardingSessionService;
import com.firstagent.backend.vision.client.PythonVisionClient;
import com.firstagent.backend.vision.dto.LivenessChallengeStart;
import com.firstagent.backend.vision.dto.LivenessChallengeVerify;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LivenessServiceImpl implements LivenessService {

  private static final String ACTIONS_SEPARATOR = ",";

  private final JournalAudit journal;
  private final StagingLivenessResultRepository stagingLivenessResultRepository;
  private final OnboardingSessionService onboardingSessionService;
  private final PythonVisionClient visionClient;

  @Override
  @Transactional
  public ChallengeStartResponse startChallenge(String sessionToken) {
    OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);

    LivenessChallengeStart challenge = visionClient.startLivenessChallenge();

    StagingLivenessResult result =
        stagingLivenessResultRepository
            .findByOnboardingSession_Id(session.getId())
            .orElseGet(() -> StagingLivenessResult.builder().onboardingSession(session).build());
    result.setSessionId(challenge.sessionId());
    result.setStatus(LivenessStatus.PENDING);
    result.setCompletedActions("");
    result.setTotalActions(challenge.actions().size());
    result.setVerifiedAt(null);
    stagingLivenessResultRepository.save(result);

    return ChallengeStartResponse.builder()
        .sessionId(challenge.sessionId())
        .actions(challenge.actions())
        .expiresInSeconds(challenge.expiresInSeconds())
        .build();
  }

  @Override
  @Transactional
  public ChallengeVerifyResponse verifyAction(
      String sessionToken, String action, List<byte[]> frames) {
    OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);
    StagingLivenessResult result = findExistingResult(session.getId());

    LivenessChallengeVerify verification =
        visionClient.verifyLivenessChallenge(result.getSessionId(), action, frames);

    result.setCompletedActions(String.join(ACTIONS_SEPARATOR, verification.completedActions()));
    if (verification.allActionsCompleted()) {
      result.setStatus(LivenessStatus.LIVE);
      result.setVerifiedAt(LocalDateTime.now());
    }
    stagingLivenessResultRepository.save(result);

    if (!verification.actionCompleted()) {
      // Signal volontairement faible pris isolément : un client peut cligner
      // trop lentement ou mal cadrer son visage. C'est leur accumulation sur
      // 24 heures que la règle d'alerte observe, une photo imprimée ou un
      // écran présenté devant l'objectif échouant systématiquement.
      journal.enregistrer(
          JournalAudit.EcritureAudit.echec(
              session.getPhoneNumber(),
              EvenementAudit.VIVACITE_ECHOUEE,
              session.identifiantActeur(),
              TypeActeur.CLIENT,
              "Action de vivacité « "
                  + action
                  + " » non réalisée ("
                  + verification.completedActions().size()
                  + " action(s) déjà validée(s))"));
    }

    return ChallengeVerifyResponse.builder()
        .sessionId(verification.sessionId())
        .action(verification.action())
        .actionCompleted(verification.actionCompleted())
        .completedActions(verification.completedActions())
        .remainingActions(verification.remainingActions())
        .allActionsCompleted(verification.allActionsCompleted())
        .build();
  }

  @Override
  public ChallengeStatusResponse getStatus(String sessionToken) {
    OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);
    StagingLivenessResult result = findExistingResult(session.getId());

    List<String> completed = splitActions(result.getCompletedActions());
    boolean allCompleted = result.getStatus() == LivenessStatus.LIVE;

    return ChallengeStatusResponse.builder()
        .sessionId(result.getSessionId())
        .actions(
            List.of()) // la liste complète des actions demandées n'est pas persistée, seule leur
        // nombre l'est
        .completedActions(completed)
        .remainingActions(List.of())
        .allActionsCompleted(allCompleted)
        .live(allCompleted)
        .build();
  }

  @Override
  public boolean isLive(String sessionToken) {
    OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);
    return stagingLivenessResultRepository.existsByOnboardingSession_IdAndStatus(
        session.getId(), LivenessStatus.LIVE);
  }

  private StagingLivenessResult findExistingResult(UUID onboardingSessionId) {
    return stagingLivenessResultRepository
        .findByOnboardingSession_Id(onboardingSessionId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException("Aucune session de vivacité trouvée pour ce client"));
  }

  private static List<String> splitActions(String joined) {
    if (joined == null || joined.isBlank()) {
      return List.of();
    }
    return Arrays.asList(joined.split(ACTIONS_SEPARATOR));
  }
}
