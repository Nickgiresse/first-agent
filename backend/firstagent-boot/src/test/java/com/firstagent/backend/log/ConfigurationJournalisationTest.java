package com.firstagent.backend.log;

import static org.assertj.core.api.Assertions.assertThat;

import com.firstagent.backend.common.log.ConvertisseurMasque;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La configuration de journalisation doit désigner un convertisseur qui existe.
 *
 * <h2>Pourquoi ce test a sa place ici</h2>
 *
 * <p>Le nom de classe écrit dans {@code logback-spring.xml} est une chaîne : le compilateur ne le
 * vérifie pas, et un renommage de paquet ou de classe le laisse en place, périmé. Logback ne se
 * plaint pas davantage. Il fait pire que ne pas masquer : il remplace le message par {@code
 * %PARSER_ERROR} et la trace est <b>perdue</b>. Tous les journaux deviennent muets, sans qu'aucune
 * erreur ne le signale, et cela ne se remarque que le jour où l'on cherche à comprendre un
 * incident.
 *
 * <p>Ce cas relie donc la chaîne du XML à la classe réelle, si bien qu'un renommage casse le test
 * au lieu de casser la journalisation en production.
 */
class ConfigurationJournalisationTest {

  private String configuration() throws IOException {
    try (InputStream flux = getClass().getClassLoader().getResourceAsStream("logback-spring.xml")) {
      assertThat(flux).as("logback-spring.xml doit être présent dans les ressources").isNotNull();
      return new String(flux.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  @DisplayName("le convertisseur déclaré correspond à une classe existante")
  void convertisseurDeclare_existe() throws IOException {
    assertThat(configuration()).contains(ConvertisseurMasque.class.getName());
  }

  @Test
  @DisplayName("tout motif d'écriture utilise le message masqué et non le message brut")
  void motif_utiliseLeMessageMasque() throws IOException {
    // La vérification porte sur le contenu des balises <pattern>, et non sur le
    // fichier entier : les commentaires citent %msg pour expliquer pourquoi il
    // ne faut pas l'employer, et les inclure ferait échouer le test sur sa
    // propre documentation.
    Matcher motifs =
        Pattern.compile("<pattern>(.*?)</pattern>", Pattern.DOTALL).matcher(configuration());

    int trouves = 0;
    while (motifs.find()) {
      String motif = motifs.group(1);
      trouves++;
      assertThat(motif).as("motif d'écriture").contains("%msgMasque");
      // %msg ou %message laisserait passer les données personnelles sans que
      // rien ne le signale : c'est la régression que ce test doit empêcher.
      assertThat(motif.replace("%msgMasque", ""))
          .as("motif d'écriture, une fois le message masqué retiré")
          .doesNotContain("%msg")
          .doesNotContain("%message")
          .doesNotContain("%m ");
    }

    assertThat(trouves).as("nombre de motifs d'écriture trouvés").isPositive();
  }
}
