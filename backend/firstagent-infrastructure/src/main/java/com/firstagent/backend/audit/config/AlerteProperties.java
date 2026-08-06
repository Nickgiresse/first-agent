package com.firstagent.backend.audit.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Réglages des alertes comportementales.
 *
 * <p>ÉCART ASSUMÉ AVEC LA SOURCE. Dans le bot WhatsApp, seuils et destinataires se règlent depuis
 * le back-office, ce qui permet à un responsable de les ajuster sans redéploiement. Ce service n'a
 * pas de back-office, et lui en inventer un pour cette seule fonction dépasserait le sujet. Les
 * réglages passent donc par la configuration, où ils restent modifiables sans recompiler, mais pas
 * sans redémarrer. À reprendre le jour où un écran d'administration existera.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.alertes")
public class AlerteProperties {

  /**
   * Envoi des courriels d'alerte.
   *
   * <p>Indépendant de la détection : les alertes sont toujours écrites dans le journal scellé, quoi
   * qu'il arrive. Ce réglage ne pilote que la notification, pour qu'une boîte saturée ou un serveur
   * de messagerie en panne ne fasse jamais perdre une trace.
   */
  private boolean envoiActif = false;

  /**
   * Personnes averties.
   *
   * <p>Une liste et non une adresse : une alerte de sécurité concerne rarement une seule personne,
   * et faire dépendre la chaîne d'alerte de la disponibilité d'un individu est un point de rupture.
   */
  private List<String> destinataires = List.of();

  /** Échecs de vérification d'identité, sur 24 heures. Signal d'usurpation. */
  private int seuilKycEchecs = 2;

  /** Codes à usage unique erronés, sur 15 minutes. Signal de force brute. */
  private int seuilOtpErrones = 3;

  /** Échecs de vivacité, sur 24 heures. Signal de présentation d'un visage enregistré. */
  private int seuilVivaciteEchecs = 3;
}
