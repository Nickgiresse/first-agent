package com.firstagent.backend.audit.service;

import com.firstagent.backend.audit.config.AlerteProperties;
import com.firstagent.backend.audit.model.EvenementAudit;
import com.firstagent.backend.audit.model.RegleAlerte;
import com.firstagent.backend.audit.model.TypeActeur;
import com.firstagent.backend.audit.port.JournalAudit;
import com.firstagent.backend.audit.repository.AuditLogRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alertes comportementales sur le journal forensique.
 *
 * <p>Le journal scellé prouve ce qui s'est passé, mais personne ne le relit en continu. Détecter
 * pendant qu'il est encore temps suppose que quelque chose le parcoure sans qu'on le lui demande :
 * c'est ce que fait ce balayage.
 *
 * <p>Chaque comportement détecté produit une entrée {@code SECURITY_ALERT}, scellée comme le reste,
 * donc elle-même infalsifiable et opposable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalayageAlertes {

  private final AuditLogRepository depot;
  private final JournalAudit journal;
  private final AlerteProperties reglages;
  private final JavaMailSender messagerie;

  @Value("${app.mail.from-address}")
  private String expediteur;

  /**
   * Règles appliquées à chaque passage.
   *
   * <p>Trois règles seulement, et c'est délibéré. Le bot WhatsApp en a cinq, mais deux d'entre
   * elles observent un déluge de messages entrants et des tentatives de manipulation du moteur
   * conversationnel : ce service n'a ni canal de messagerie ni moteur conversationnel, et porter
   * ces règles reviendrait à surveiller ce qui ne peut pas se produire.
   */
  private List<RegleAlerte> regles() {
    return List.of(
        new RegleAlerte(
            "kyc_echecs",
            List.of(EvenementAudit.CONTROLE_KYC, EvenementAudit.ONBOARDING_BLOQUE),
            Duration.ofHours(24),
            reglages.getSeuilKycEchecs(),
            "%d échecs de vérification d'identité en 24 h pour %s : "
                + "tentative d'usurpation possible."),
        new RegleAlerte(
            "otp_bruteforce",
            List.of(EvenementAudit.OTP_ERRONE, EvenementAudit.OTP_VERROUILLE),
            Duration.ofMinutes(15),
            reglages.getSeuilOtpErrones(),
            "%d codes de vérification erronés en 15 minutes pour %s : "
                + "tentative de force brute probable."),
        new RegleAlerte(
            "vivacite_echecs",
            List.of(EvenementAudit.VIVACITE_ECHOUEE, EvenementAudit.FACIAL_REFUSE),
            Duration.ofHours(24),
            reglages.getSeuilVivaciteEchecs(),
            "%d échecs de vivacité ou de comparaison faciale en 24 h pour %s : "
                + "présentation d'un visage enregistré possible."));
  }

  /**
   * Applique toutes les règles et retourne le nombre d'alertes créées.
   *
   * <p>Conçu pour être appelé souvent : chaque requête est bornée par une fenêtre temporelle
   * indexée, et la déduplication empêche de réécrire la même alerte à chaque passage.
   */
  @Scheduled(fixedDelayString = "${app.alertes.intervalle-balayage-ms:300000}")
  @Transactional(readOnly = true)
  public int balayer() {
    int creees = 0;
    for (RegleAlerte regle : regles()) {
      if (!regle.active()) {
        continue;
      }
      try {
        creees += appliquer(regle);
      } catch (RuntimeException e) {
        // Une règle en échec ne doit pas empêcher les autres de s'exécuter :
        // ce serait le meilleur moyen de perdre toute la surveillance sur un
        // défaut isolé.
        log.error("[ALERTE] règle {} en échec : {}", regle.code(), e.getMessage(), e);
      }
    }
    return creees;
  }

  private int appliquer(RegleAlerte regle) {
    Instant depuis = Instant.now().minus(regle.fenetre());
    int creees = 0;

    for (AuditLogRepository.ComptageParTelephone ligne :
        depot.compterEchecsParTelephone(regle.typesEvenements(), depuis)) {
      String telephone = ligne.getTelephone();
      long occurrences = ligne.getOccurrences();

      if (!regle.declenche(occurrences)) {
        continue;
      }
      String reference = regle.reference(telephone);
      if (depot.existsByEventTypeAndReferenceAndTimestampGreaterThanEqual(
          EvenementAudit.ALERTE_SECURITE, reference, depuis)) {
        continue;
      }

      alerter(telephone, reference, regle.message(occurrences, telephone));
      creees++;
    }
    return creees;
  }

  /** Écrit l'alerte dans le journal scellé, puis prévient le groupe destinataire. */
  private void alerter(String telephone, String reference, String message) {
    journal.enregistrer(
        new JournalAudit.EcritureAudit(
            telephone,
            EvenementAudit.ALERTE_SECURITE,
            "FAILURE",
            "system",
            TypeActeur.SYSTEM,
            null,
            null,
            null,
            reference,
            message));
    log.warn("[ALERTE] {} : {}", reference, message);

    notifier(reference, message);
  }

  private void notifier(String reference, String message) {
    if (!reglages.isEnvoiActif() || reglages.getDestinataires().isEmpty()) {
      return;
    }
    for (String adresse : reglages.getDestinataires()) {
      try {
        SimpleMailMessage courriel = new SimpleMailMessage();
        courriel.setFrom(expediteur);
        courriel.setTo(adresse);
        courriel.setSubject("[FirstAgent] Alerte sécurité : " + reference);
        courriel.setText(
            message
                + "\n\nHorodatage : "
                + Instant.now()
                + "\nLe détail figure dans le journal d'audit.");
        messagerie.send(courriel);
      } catch (RuntimeException e) {
        // Un destinataire injoignable ne doit pas priver les autres, et un
        // envoi manqué ne doit surtout pas remettre en cause la trace, qui
        // est déjà écrite.
        log.error("[ALERTE] envoi à {} échoué : {}", adresse, e.getMessage());
      }
    }
  }
}
