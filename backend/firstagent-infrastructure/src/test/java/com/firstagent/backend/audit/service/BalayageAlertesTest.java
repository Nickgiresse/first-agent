package com.firstagent.backend.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.firstagent.backend.audit.config.AlerteProperties;
import com.firstagent.backend.audit.model.EvenementAudit;
import com.firstagent.backend.audit.port.JournalAudit;
import com.firstagent.backend.audit.repository.AuditLogRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

/** Le balayage doit alerter quand il faut, et surtout se taire le reste du temps. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BalayageAlertesTest {

  private static final String NUMERO = "+237699000001";

  @Mock private AuditLogRepository depot;
  @Mock private JournalAudit journal;
  @Mock private JavaMailSender messagerie;

  private AlerteProperties reglages;
  private BalayageAlertes balayage;

  @BeforeEach
  void setUp() {
    reglages = new AlerteProperties();
    // Une seule règle active à la fois, pour que chaque test porte sur elle
    // seule : sinon un déclenchement pourrait venir d'une autre règle et le
    // test passerait pour de mauvaises raisons.
    reglages.setSeuilKycEchecs(2);
    reglages.setSeuilOtpErrones(0);
    reglages.setSeuilVivaciteEchecs(0);

    balayage = new BalayageAlertes(depot, journal, reglages, messagerie);
    ReflectionTestUtils.setField(balayage, "expediteur", "alertes@exemple.invalid");

    when(depot.compterEchecsParTelephone(anyList(), any())).thenReturn(List.of());
    when(depot.existsByEventTypeAndReferenceAndTimestampGreaterThanEqual(
            anyString(), anyString(), any()))
        .thenReturn(false);
  }

  private void compte(long occurrences) {
    when(depot.compterEchecsParTelephone(
            eq(List.of(EvenementAudit.CONTROLE_KYC, EvenementAudit.ONBOARDING_BLOQUE)), any()))
        .thenReturn(List.of(comptage(NUMERO, occurrences)));
  }

  /** Projection de comptage, telle que la base la rendrait. */
  private AuditLogRepository.ComptageParTelephone comptage(String telephone, long occurrences) {
    return new AuditLogRepository.ComptageParTelephone() {
      @Override
      public String getTelephone() {
        return telephone;
      }

      @Override
      public long getOccurrences() {
        return occurrences;
      }
    };
  }

  @Test
  @DisplayName("le seuil atteint déclenche une alerte scellée")
  void balayer_seuilAtteint_alerte() {
    compte(3);

    assertThat(balayage.balayer()).isEqualTo(1);

    ArgumentCaptor<JournalAudit.EcritureAudit> trace =
        ArgumentCaptor.forClass(JournalAudit.EcritureAudit.class);
    verify(journal).enregistrer(trace.capture());
    assertThat(trace.getValue().typeEvenement()).isEqualTo(EvenementAudit.ALERTE_SECURITE);
    assertThat(trace.getValue().reference()).isEqualTo("kyc_echecs:" + NUMERO);
    assertThat(trace.getValue().details()).contains("3 échecs").contains("usurpation");
    // L'alerte n'est le geste de personne : elle est imputée au système.
    assertThat(trace.getValue().acteur()).isEqualTo("system");
  }

  @Test
  @DisplayName("sous le seuil, rien n'est écrit")
  void balayer_sousLeSeuil_seTait() {
    compte(1);

    assertThat(balayage.balayer()).isZero();
    verifyNoInteractions(journal);
  }

  @Test
  @DisplayName("une alerte déjà émise dans la fenêtre n'est pas réécrite")
  void balayer_dejaAlerte_neRepetePas() {
    compte(5);
    when(depot.existsByEventTypeAndReferenceAndTimestampGreaterThanEqual(
            eq(EvenementAudit.ALERTE_SECURITE), eq("kyc_echecs:" + NUMERO), any(Instant.class)))
        .thenReturn(true);

    // Sans déduplication, la même alerte serait réécrite à chaque passage tant
    // que la cause dure, et la répétition rendrait le signal inaudible.
    assertThat(balayage.balayer()).isZero();
    verifyNoInteractions(journal);
  }

  @Test
  @DisplayName("un seuil à zéro désactive la règle")
  void balayer_seuilZero_desactiveLaRegle() {
    reglages.setSeuilKycEchecs(0);
    compte(99);

    assertThat(balayage.balayer()).isZero();
    verifyNoInteractions(journal);
  }

  @Test
  @DisplayName("une règle en échec n'empêche pas les autres de s'exécuter")
  void balayer_regleEnEchec_nInterromptPasLesAutres() {
    reglages.setSeuilOtpErrones(2);
    // La règle KYC tombe, la règle OTP doit tout de même s'appliquer.
    when(depot.compterEchecsParTelephone(
            eq(List.of(EvenementAudit.CONTROLE_KYC, EvenementAudit.ONBOARDING_BLOQUE)), any()))
        .thenThrow(new IllegalStateException("base indisponible"));
    when(depot.compterEchecsParTelephone(
            eq(List.of(EvenementAudit.OTP_ERRONE, EvenementAudit.OTP_VERROUILLE)), any()))
        .thenReturn(List.of(comptage(NUMERO, 4L)));

    assertThat(balayage.balayer()).isEqualTo(1);
    verify(journal).enregistrer(any());
  }

  @Test
  @DisplayName("l'envoi désactivé n'empêche pas l'écriture de la trace")
  void balayer_envoiDesactive_traceQuandMeme() {
    reglages.setEnvoiActif(false);
    reglages.setDestinataires(List.of("securite@exemple.invalid"));
    compte(3);

    balayage.balayer();

    // La détection ne dépend pas de la messagerie : une boîte saturée ne doit
    // jamais faire perdre une trace.
    verify(journal).enregistrer(any());
    verifyNoInteractions(messagerie);
  }

  @Test
  @DisplayName("chaque destinataire est prévenu séparément")
  void balayer_envoiActif_previentToutLeGroupe() {
    reglages.setEnvoiActif(true);
    reglages.setDestinataires(List.of("un@exemple.invalid", "deux@exemple.invalid"));
    compte(3);

    balayage.balayer();

    verify(messagerie, times(2)).send(any(org.springframework.mail.SimpleMailMessage.class));
  }

  @Test
  @DisplayName("un destinataire injoignable ne prive pas les autres")
  void balayer_envoiEnEchec_nInterromptPasLeGroupe() {
    reglages.setEnvoiActif(true);
    reglages.setDestinataires(List.of("casse@exemple.invalid", "ok@exemple.invalid"));
    compte(3);
    org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("injoignable"))
        .doNothing()
        .when(messagerie)
        .send(any(org.springframework.mail.SimpleMailMessage.class));

    balayage.balayer();

    // Les deux envois sont tentés, et la trace reste écrite quoi qu'il arrive.
    verify(messagerie, times(2)).send(any(org.springframework.mail.SimpleMailMessage.class));
    verify(journal).enregistrer(any());
  }

  @Test
  @DisplayName("un journal sans échec ne produit aucune alerte")
  void balayer_journalCalme_neProduitRien() {
    assertThat(balayage.balayer()).isZero();
    verifyNoInteractions(journal);
    verify(messagerie, never()).send(any(org.springframework.mail.SimpleMailMessage.class));
  }
}
