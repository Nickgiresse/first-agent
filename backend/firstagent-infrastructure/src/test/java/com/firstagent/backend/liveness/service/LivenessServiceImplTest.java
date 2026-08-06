package com.firstagent.backend.liveness.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.firstagent.backend.audit.model.EvenementAudit;
import com.firstagent.backend.audit.port.JournalAudit;
import com.firstagent.backend.common.enums.LivenessStatus;
import com.firstagent.backend.liveness.dto.ChallengeVerifyResponse;
import com.firstagent.backend.liveness.entity.StagingLivenessResult;
import com.firstagent.backend.liveness.repository.StagingLivenessResultRepository;
import com.firstagent.backend.onboarding.entity.OnboardingSession;
import com.firstagent.backend.onboarding.service.OnboardingSessionService;
import com.firstagent.backend.vision.client.PythonVisionClient;
import com.firstagent.backend.vision.dto.LivenessChallengeVerify;
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

/** Vérification des défis de vivacité et de leur trace. */
@ExtendWith(MockitoExtension.class)
class LivenessServiceImplTest {

  private static final String JETON = "session-token";
  private static final String NUMERO = "+237699000001";

  @Mock private JournalAudit journal;
  @Mock private StagingLivenessResultRepository depot;
  @Mock private OnboardingSessionService sessions;
  @Mock private PythonVisionClient vision;

  private LivenessServiceImpl service;

  private final UUID sessionId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new LivenessServiceImpl(journal, depot, sessions, vision);
  }

  private OnboardingSession session() {
    return OnboardingSession.builder().id(sessionId).phoneNumber(NUMERO).build();
  }

  private StagingLivenessResult resultatEnCours() {
    return StagingLivenessResult.builder()
        .sessionId("vision-session")
        .status(LivenessStatus.PENDING)
        .totalActions(3)
        .completedActions("")
        .build();
  }

  private void avecVerification(LivenessChallengeVerify verification) {
    when(sessions.getValidSession(JETON)).thenReturn(session());
    when(depot.findByOnboardingSession_Id(sessionId)).thenReturn(Optional.of(resultatEnCours()));
    when(vision.verifyLivenessChallenge(any(), any(), any())).thenReturn(verification);
  }

  @Test
  @DisplayName("une action réussie ne laisse pas de trace")
  void verifyAction_reussie_neJournalisePas() {
    avecVerification(
        new LivenessChallengeVerify(
            "vision-session", "BLINK", true, List.of("BLINK"), List.of("SMILE"), false));

    ChallengeVerifyResponse reponse = service.verifyAction(JETON, "BLINK", List.of(new byte[] {1}));

    assertThat(reponse.isActionCompleted()).isTrue();
    // Un journal qui enregistre aussi les succès noierait le signal.
    verifyNoInteractions(journal);
  }

  @Test
  @DisplayName("une action manquée est journalisée")
  void verifyAction_manquee_estJournalisee() {
    avecVerification(
        new LivenessChallengeVerify(
            "vision-session", "SMILE", false, List.of("BLINK"), List.of("SMILE"), false));

    service.verifyAction(JETON, "SMILE", List.of(new byte[] {1}));

    ArgumentCaptor<JournalAudit.EcritureAudit> trace =
        ArgumentCaptor.forClass(JournalAudit.EcritureAudit.class);
    verify(journal).enregistrer(trace.capture());
    assertThat(trace.getValue().typeEvenement()).isEqualTo(EvenementAudit.VIVACITE_ECHOUEE);
    assertThat(trace.getValue().statut()).isEqualTo("FAILURE");
    assertThat(trace.getValue().details()).contains("SMILE");
    assertThat(trace.getValue().acteur()).isEqualTo(NUMERO);
  }

  @Test
  @DisplayName("le dernier défi réussi marque la session comme vivante")
  void verifyAction_dernierDefi_marqueVivant() {
    avecVerification(
        new LivenessChallengeVerify(
            "vision-session",
            "TURN_LEFT",
            true,
            List.of("BLINK", "SMILE", "TURN_LEFT"),
            List.of(),
            true));

    service.verifyAction(JETON, "TURN_LEFT", List.of(new byte[] {1}));

    ArgumentCaptor<StagingLivenessResult> enregistre =
        ArgumentCaptor.forClass(StagingLivenessResult.class);
    verify(depot).save(enregistre.capture());
    assertThat(enregistre.getValue().getStatus()).isEqualTo(LivenessStatus.LIVE);
    assertThat(enregistre.getValue().getVerifiedAt()).isNotNull();
  }

  @Test
  @DisplayName("l'acteur retombe sur la session quand le numéro manque")
  void verifyAction_sansNumero_retombeSurLaSession() {
    when(sessions.getValidSession(JETON))
        .thenReturn(OnboardingSession.builder().id(sessionId).build());
    when(depot.findByOnboardingSession_Id(sessionId)).thenReturn(Optional.of(resultatEnCours()));
    when(vision.verifyLivenessChallenge(any(), any(), any()))
        .thenReturn(
            new LivenessChallengeVerify(
                "vision-session", "BLINK", false, List.of(), List.of("BLINK"), false));

    service.verifyAction(JETON, "BLINK", List.of(new byte[] {1}));

    ArgumentCaptor<JournalAudit.EcritureAudit> trace =
        ArgumentCaptor.forClass(JournalAudit.EcritureAudit.class);
    verify(journal).enregistrer(trace.capture());
    // Une trace qui ne nomme personne reste exploitable ; une trace absente non.
    assertThat(trace.getValue().acteur()).isEqualTo("session:" + sessionId);
  }
}
