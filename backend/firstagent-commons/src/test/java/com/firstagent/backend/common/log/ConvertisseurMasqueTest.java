package com.firstagent.backend.common.log;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Le masquage doit s'appliquer en traversant réellement Logback.
 *
 * <p>Vérifier la seule fonction de masquage laisserait le branchement hors du filet : un mot de
 * conversion mal déclaré, une classe introuvable, et le masquage ne s'applique plus sans que rien
 * n'échoue. Ces cas passent donc par un {@link PatternLayout}, comme en production.
 */
class ConvertisseurMasqueTest {

  private PatternLayout formateur;

  @BeforeEach
  void setUp() {
    LoggerContext contexte = (LoggerContext) LoggerFactory.getILoggerFactory();

    formateur = new PatternLayout();
    formateur.setContext(contexte);
    // Même déclaration que la `conversionRule` de logback-spring.xml, exprimée
    // ici dans l'API : le mot `msgMasque` est relié au convertisseur.
    formateur.getInstanceConverterMap().put("msgMasque", ConvertisseurMasque::new);
    formateur.setPattern("%msgMasque");
    formateur.start();
  }

  private String formater(String message, Object... arguments) {
    Logger logger = (Logger) LoggerFactory.getLogger(ConvertisseurMasqueTest.class);
    ILoggingEvent evenement =
        new LoggingEvent(
            ConvertisseurMasqueTest.class.getName(), logger, Level.INFO, message, null, arguments);
    return formateur.doLayout(evenement);
  }

  @Test
  @DisplayName("un numéro passé en paramètre est masqué à l'écriture")
  void parametre_estMasque() {
    // Le cas courant : le message porte un {} et la donnée arrive en argument.
    // Masquer avant le formatage laisserait passer celui-ci.
    String trace = formater("Client {} bloqué", "+237699000001");

    assertThat(trace).isEqualTo("Client +23769…01 bloqué").doesNotContain("+237699000001");
  }

  @Test
  @DisplayName("un RIB dans le texte du message est masqué")
  void messageLitteral_estMasque() {
    assertThat(formater("Virement vers 10005 00001 00000075827 81"))
        .isEqualTo("Virement vers 10005…81");
  }

  @Test
  @DisplayName("une référence d'alerte ne laisse pas fuiter le numéro qu'elle contient")
  void referenceAlerte_estMasquee() {
    // Cas réel : la référence d'une alerte est « règle:cible », et la cible
    // est le numéro du client. Sans masquage, chaque alerte le publierait.
    assertThat(formater("[ALERTE] {} : {}", "kyc_echecs:+237699000001", "3 échecs"))
        .doesNotContain("+237699000001")
        .contains("kyc_echecs:+23769…01");
  }

  @Test
  @DisplayName("un message sans donnée personnelle traverse inchangé")
  void messageNeutre_traverseInchange() {
    assertThat(formater("Règle {} en échec : {}", "kyc_echecs", "base indisponible"))
        .isEqualTo("Règle kyc_echecs en échec : base indisponible");
  }
}
