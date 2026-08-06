package com.firstagent.backend.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.firstagent.backend.audit.model.EvenementAudit;
import com.firstagent.backend.audit.model.ResultatVerification;
import com.firstagent.backend.audit.port.JournalAudit;
import com.firstagent.backend.audit.repository.AuditLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;

/**
 * Exécute réellement les requêtes du journal contre PostgreSQL.
 *
 * <p>Les tests unitaires du journal simulent le dépôt : ils vérifient l'algorithme de chaînage mais
 * n'exécutent jamais une seule requête. Le démarrage du contexte prouve que le JPQL se parse, pas
 * qu'il s'exécute ni que la projection se relie. C'est ce que ces cas couvrent.
 *
 * <p>UNIQUEMENT EN LECTURE, délibérément. Le déclencheur d'immuabilité posé par la migration V18
 * interdit la suppression : des entrées écrites ici resteraient définitivement dans le journal de
 * l'environnement. Un journal d'audit est fait pour cela, et c'est précisément pourquoi un test n'a
 * rien à y écrire.
 */
@SpringBootTest(
    properties = {
      "app.jwt.secret=test-secret-that-is-long-enough-for-hmac-sha256",
      "spring.mail.host=localhost"
    })
class JournalAuditRequetesTest {

  @Autowired private AuditLogRepository depot;
  @Autowired private JournalAudit journal;

  @Test
  @DisplayName("la pagination par clé s'exécute contre la base")
  void pagination_sExecute() {
    // Le couple (timestamp, id) et le paramètre Limit sont les deux points où
    // une requête écrite à la main peut passer la compilation et échouer à
    // l'exécution.
    assertThatCode(() -> depot.premierePage(Limit.of(10))).doesNotThrowAnyException();
    assertThatCode(() -> depot.pageApres(Instant.now(), UUID.randomUUID(), Limit.of(10)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("le comptage par téléphone se relie à la projection")
  void comptage_seRelieALaProjection() {
    // Une projection dont un accesseur ne correspondrait pas à l'alias de la
    // requête échoue à l'exécution, jamais à la compilation.
    assertThatCode(
            () ->
                depot.compterEchecsParTelephone(
                    List.of(EvenementAudit.CONTROLE_KYC), Instant.now().minusSeconds(3600)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("la recherche d'une alerte déjà émise s'exécute")
  void deduplication_sExecute() {
    assertThat(
            depot.existsByEventTypeAndReferenceAndTimestampGreaterThanEqual(
                EvenementAudit.ALERTE_SECURITE, "regle:inexistante", Instant.now()))
        .isFalse();
  }

  @Test
  @DisplayName("la vérification de la chaîne parcourt le journal réel")
  void verification_parcourtLeJournalReel() {
    ResultatVerification resultat = journal.verifier(null);

    // Sur un journal intact, quel que soit son contenu, la chaîne tient.
    assertThat(resultat.intact()).isTrue();
    assertThat(resultat.rupture()).isNull();
  }

  @Test
  @DisplayName("l'export SIEM produit du JSON par ligne")
  void export_produitDuJsonParLigne() {
    try (Stream<String> lignes = journal.exporterJsonl(null, 5L)) {
      // Le journal peut être vide ; ce qui est vérifié ici est que la requête
      // s'exécute et que chaque ligne produite est un objet JSON complet.
      assertThat(lignes).allSatisfy(l -> assertThat(l).startsWith("{").endsWith("}"));
    }
  }
}
