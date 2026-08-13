package com.firstagent.backend.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.firstagent.backend.liveness.entity.StagingLivenessResult;
import com.firstagent.backend.liveness.repository.StagingLivenessResultRepository;
import com.firstagent.backend.onboarding.entity.OnboardingSession;
import com.firstagent.backend.onboarding.service.OnboardingSessionService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FaceVerificationServiceImplTest {

  @Mock private JournalAudit journal;
  @Mock private StagingDocumentRepository stagingDocumentRepository;
  @Mock private StagingFaceVerificationResultRepository stagingFaceVerificationResultRepository;
  @Mock private OnboardingSessionService onboardingSessionService;
  @Mock private StorageService storageService;
  @Mock private FaceMatchProvider faceMatchProvider;
  @Mock private StagingLivenessResultRepository stagingLivenessResultRepository;

  private FaceVerificationServiceImpl service;

  private final String sessionToken = "session-token";
  private final UUID sessionId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new FaceVerificationServiceImpl(
            stagingDocumentRepository,
            stagingFaceVerificationResultRepository,
            stagingLivenessResultRepository,
            onboardingSessionService,
            journal,
            storageService,
            faceMatchProvider);
    // Champ @Value : Spring ne l'injecte pas dans un test unitaire, et sa valeur nulle
    // ferait tomber la comparaison de mode. On pose le defaut de production.
    ReflectionTestUtils.setField(service, "modeLiaisonVivacite", "strict");
  }

  private OnboardingSession session() {
    return OnboardingSession.builder().id(sessionId).build();
  }

  @Test
  void verifyFaceThrowsWhenCniPhotoMissing() {
    when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session());
    when(stagingDocumentRepository.findByOnboardingSession_IdAndDocumentType(
            sessionId, DocumentType.CNI_RECTO))
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
    when(stagingDocumentRepository.findByOnboardingSession_IdAndDocumentType(
            sessionId, DocumentType.CNI_RECTO))
        .thenReturn(Optional.of(cniPhoto));
    when(stagingDocumentRepository.findByOnboardingSession_IdAndDocumentType(
            sessionId, DocumentType.SELFIE))
        .thenReturn(Optional.of(selfie));
    when(storageService.read("/cni.jpg")).thenReturn(new byte[] {1});
    when(storageService.read("/selfie.jpg")).thenReturn(new byte[] {2});
    when(stagingLivenessResultRepository.findByOnboardingSession_Id(sessionId))
        .thenReturn(Optional.of(StagingLivenessResult.builder().sessionId("defi-1").build()));
    when(faceMatchProvider.compareFaces(any(), any(), any()))
        .thenReturn(new FaceMatchResult(true, 92.5, 88.0, true));
    when(faceMatchProvider.getProviderName()).thenReturn("MOCK");
    when(stagingFaceVerificationResultRepository.findByOnboardingSession_Id(sessionId))
        .thenReturn(Optional.empty());
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
    when(stagingDocumentRepository.findByOnboardingSession_IdAndDocumentType(
            sessionId, DocumentType.CNI_RECTO))
        .thenReturn(Optional.of(cniPhoto));
    when(stagingDocumentRepository.findByOnboardingSession_IdAndDocumentType(
            sessionId, DocumentType.SELFIE))
        .thenReturn(Optional.of(selfie));
    when(storageService.read("/cni.jpg")).thenReturn(new byte[] {1});
    when(storageService.read("/selfie.jpg")).thenReturn(new byte[] {2});
    when(stagingLivenessResultRepository.findByOnboardingSession_Id(sessionId))
        .thenReturn(Optional.of(StagingLivenessResult.builder().sessionId("defi-1").build()));
    when(faceMatchProvider.compareFaces(any(), any(), any()))
        .thenReturn(new FaceMatchResult(false, 12.0, 55.0, true));
    when(faceMatchProvider.getProviderName()).thenReturn("MOCK");
    when(stagingFaceVerificationResultRepository.findByOnboardingSession_Id(sessionId))
        .thenReturn(Optional.empty());
    when(stagingFaceVerificationResultRepository.save(any(StagingFaceVerificationResult.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThatThrownBy(() -> service.verifyFace(sessionToken))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("ne correspond pas");

    verify(stagingFaceVerificationResultRepository)
        .save(argThat(saved -> saved.getStatus() == FaceVerificationStatus.FAILED));
  }

  @Test
  void getVerificationThrowsWhenNoResultExists() {
    when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session());
    when(stagingFaceVerificationResultRepository.findByOnboardingSession_Id(sessionId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getVerification(sessionToken))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void isVerifiedDelegatesToRepository() {
    when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session());
    when(stagingFaceVerificationResultRepository.existsByOnboardingSession_IdAndStatus(
            sessionId, FaceVerificationStatus.VERIFIED))
        .thenReturn(true);

    assertThat(service.isVerified(sessionToken)).isTrue();
  }

  /**
   * Le coeur de la correction de la liaison vivacite-visage.
   *
   * <p>La comparaison faciale reussit : le selfie ressemble bien a la CNI. Mais rien n'etablit que
   * ce selfie est celui de la personne qui a joue le defi. Accepter reviendrait a laisser une
   * personne prouver sa vivacite et une autre son identite.
   */
  @Test
  void verifyFaceRefuseUnSelfieNonRattacheAuDefiDeVivacite() {
    preparerComparaison(new FaceMatchResult(true, 92.5, 88.0, false), "defi-1");

    assertThatThrownBy(() -> service.verifyFace(sessionToken))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("test de vivacite".replace("vivacite", "vivacité"));
  }

  /**
   * Appel hors sequence : aucun defi n'a ete joue pour cette session.
   *
   * <p>Le parcours declenche toujours la comparaison apres le defi, dans le meme ecran. Y arriver
   * sans defi signifie que l'API a ete appelee directement, ce qui est precisement le chemin qu'un
   * fraudeur emprunterait pour eviter la liaison.
   */
  @Test
  void verifyFaceRefuseQuandAucunDefiDeVivaciteN_aEteJoue() {
    preparerComparaison(new FaceMatchResult(true, 92.5, 88.0, null), null);

    assertThatThrownBy(() -> service.verifyFace(sessionToken))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("test de vivacite".replace("vivacite", "vivacité"));
  }

  /**
   * Le mode de secours journalise mais laisse passer. Il rouvre la faille : a n'utiliser qu'en
   * urgence.
   */
  @Test
  void verifyFaceLaissePasserUnSelfieNonRattacheQuandLaLiaisonEstDesactivee() {
    ReflectionTestUtils.setField(service, "modeLiaisonVivacite", "off");
    preparerComparaison(new FaceMatchResult(true, 92.5, 88.0, false), "defi-1");

    FaceVerificationResponse response = service.verifyFace(sessionToken);

    assertThat(response.isMatched()).isTrue();
  }

  /** Montage commun des trois tests de liaison ci-dessus. */
  private void preparerComparaison(FaceMatchResult resultat, String idDefi) {
    StagingDocument cniPhoto = StagingDocument.builder().filePath("/cni.jpg").build();
    StagingDocument selfie = StagingDocument.builder().filePath("/selfie.jpg").build();

    when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session());
    when(stagingDocumentRepository.findByOnboardingSession_IdAndDocumentType(
            sessionId, DocumentType.CNI_RECTO))
        .thenReturn(Optional.of(cniPhoto));
    when(stagingDocumentRepository.findByOnboardingSession_IdAndDocumentType(
            sessionId, DocumentType.SELFIE))
        .thenReturn(Optional.of(selfie));
    when(storageService.read("/cni.jpg")).thenReturn(new byte[] {1});
    when(storageService.read("/selfie.jpg")).thenReturn(new byte[] {2});
    when(stagingLivenessResultRepository.findByOnboardingSession_Id(sessionId))
        .thenReturn(
            idDefi == null
                ? Optional.empty()
                : Optional.of(StagingLivenessResult.builder().sessionId(idDefi).build()));
    when(faceMatchProvider.compareFaces(any(), any(), any())).thenReturn(resultat);
    when(faceMatchProvider.getProviderName()).thenReturn("MOCK");
    when(stagingFaceVerificationResultRepository.findByOnboardingSession_Id(sessionId))
        .thenReturn(Optional.empty());
    when(stagingFaceVerificationResultRepository.save(any(StagingFaceVerificationResult.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }
}
