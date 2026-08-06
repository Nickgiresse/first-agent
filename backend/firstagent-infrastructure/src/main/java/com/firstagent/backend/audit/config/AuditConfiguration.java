package com.firstagent.backend.audit.config;

import com.firstagent.backend.audit.model.ScelleurAudit;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Fournit la clé de scellement du journal.
 *
 * <p>Le secret ne vient jamais de la base : une clé rangée à côté des données qu'elle protège ne
 * protège de rien face à qui a la main sur la machine. Elle est lue dans la configuration, où elle
 * peut être injectée depuis un coffre.
 */
@Slf4j
@Configuration
public class AuditConfiguration {

  private static final String PROFIL_PRODUCTION = "prod";

  @Value("${app.audit.hmac-key:}")
  private String cleConfiguree;

  @Bean
  public ScelleurAudit scelleurAudit(Environment environnement) {
    boolean production = environnement.matchesProfiles(PROFIL_PRODUCTION);
    String brute = cleConfiguree == null ? "" : cleConfiguree.trim();

    if (brute.isEmpty()) {
      if (production) {
        // En production, démarrer sans clé reviendrait à sceller avec un
        // secret volatil : au prochain redémarrage, toute la chaîne
        // paraîtrait falsifiée. Mieux vaut refuser de démarrer.
        throw new IllegalStateException(
            "app.audit.hmac-key est obligatoire en production : sans elle, le journal serait "
                + "scellé par une clé éphémère et deviendrait invérifiable au redémarrage. "
                + "Injectez-la depuis un coffre.");
      }
      log.warn(
          "[AUDIT] Aucune clé de scellement configurée : une clé éphémère est utilisée. "
              + "Le journal sera invérifiable après redémarrage. Acceptable en développement "
              + "seulement ; définissez app.audit.hmac-key ailleurs.");
      byte[] ephemere = new byte[32];
      new SecureRandom().nextBytes(ephemere);
      return new ScelleurAudit(ephemere);
    }

    ScelleurAudit scelleur = new ScelleurAudit(octets(brute));
    if (scelleur.cleTropCourte()) {
      log.warn(
          "[AUDIT] La clé de scellement fait moins de 32 octets. HMAC-SHA256 en attend au moins "
              + "autant pour conserver sa marge de sécurité.");
    }
    return scelleur;
  }

  /**
   * Interprète la clé en hexadécimal si elle en a la forme, en texte sinon.
   *
   * <p>Accepter les deux évite le piège classique : une clé de 64 caractères hexadécimaux traitée
   * comme du texte donne 64 octets d'entropie apparente pour 32 octets réels, et le décalage passe
   * inaperçu.
   */
  private byte[] octets(String brute) {
    try {
      return HexFormat.of().parseHex(brute);
    } catch (IllegalArgumentException nonHexadecimal) {
      return brute.getBytes(StandardCharsets.UTF_8);
    }
  }
}
