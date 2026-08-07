package com.firstagent.backend.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.firstagent.backend.audit.entity.AuditLogEntry;
import com.firstagent.backend.audit.model.EntreeAudit;
import com.firstagent.backend.audit.model.ResultatVerification;
import com.firstagent.backend.audit.model.ScelleurAudit;
import com.firstagent.backend.audit.model.TypeActeur;
import com.firstagent.backend.audit.port.JournalAudit;
import com.firstagent.backend.audit.repository.AuditLogRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;

/**
 * La vérification doit désigner la falsification, pas seulement l'affirmer.
 *
 * <p>Les tests s'appuient sur un dépôt simulé plutôt que sur la base réelle. Ce n'est pas un
 * raccourci : le déclencheur d'immuabilité posé par la migration V18 interdit la suppression, si
 * bien que des entrées de test écrites en base y resteraient définitivement. L'algorithme de
 * chaînage, lui, se vérifie intégralement en mémoire.
 */
class JournalAuditJpaTest {

  private static final byte[] CLE =
      "cle-de-test-de-trente-deux-octets-minimum".getBytes(StandardCharsets.UTF_8);

  private AuditLogRepository depot;
  private ScelleurAudit scelleur;
  private JournalAuditJpa journal;

  @BeforeEach
  void setUp() {
    depot = mock(AuditLogRepository.class);
    scelleur = new ScelleurAudit(CLE);
    journal = new JournalAuditJpa(depot, scelleur);
  }

  @Test
  @DisplayName("une chaîne intacte est reconnue intacte")
  void verifier_chaineIntacte() {
    simulerJournal(chaineDe(5));

    ResultatVerification resultat = journal.verifier(null);

    assertThat(resultat.intact()).isTrue();
    assertThat(resultat.verifiees()).isEqualTo(5);
    assertThat(resultat.rupture()).isNull();
  }

  @Test
  @DisplayName("une entrée modifiée après scellement est détectée et désignée")
  void verifier_contenuModifie() {
    List<AuditLogEntry> entrees = chaineDe(5);
    // Falsification typique : on réécrit le montant d'une opération déjà
    // journalisée, sans toucher aux empreintes.
    AuditLogEntry cible = entrees.get(2);
    AuditLogEntry falsifiee = copieAvecDetails(cible, "montant réécrit après coup");
    entrees.set(2, falsifiee);
    simulerJournal(entrees);

    ResultatVerification resultat = journal.verifier(null);

    assertThat(resultat.intact()).isFalse();
    assertThat(resultat.rupture().motif())
        .isEqualTo(ResultatVerification.MotifRupture.CONTENU_MODIFIE);
    assertThat(resultat.rupture().identifiant()).isEqualTo(cible.getId());
    // Les deux premières restent valides : la rupture est localisée, pas globale.
    assertThat(resultat.verifiees()).isEqualTo(2);
  }

  @Test
  @DisplayName("une entrée supprimée rompt le chaînage et la suivante le révèle")
  void verifier_entreeSupprimee() {
    List<AuditLogEntry> entrees = chaineDe(5);
    AuditLogEntry orpheline = entrees.get(3);
    entrees.remove(2); // la 3e disparaît : la 4e pointe vers un maillon absent
    simulerJournal(entrees);

    ResultatVerification resultat = journal.verifier(null);

    assertThat(resultat.intact()).isFalse();
    assertThat(resultat.rupture().motif())
        .isEqualTo(ResultatVerification.MotifRupture.CHAINAGE_ROMPU);
    assertThat(resultat.rupture().identifiant()).isEqualTo(orpheline.getId());
  }

  @Test
  @DisplayName("deux entrées interverties rompent le chaînage")
  void verifier_entreesReordonnees() {
    List<AuditLogEntry> entrees = chaineDe(5);
    java.util.Collections.swap(entrees, 1, 3);
    simulerJournal(entrees);

    ResultatVerification resultat = journal.verifier(null);

    assertThat(resultat.intact()).isFalse();
    assertThat(resultat.rupture().motif())
        .isEqualTo(ResultatVerification.MotifRupture.CHAINAGE_ROMPU);
  }

  @Test
  @DisplayName("les entrées antérieures au scellement sont comptées à part, sans rompre la chaîne")
  void verifier_entreesNonScellees() {
    List<AuditLogEntry> entrees = new ArrayList<>();
    entrees.add(entree(0, EntreeAudit.GENESE, null)); // écrite avant le scellement
    entrees.addAll(chaineDe(3));
    simulerJournal(entrees);

    ResultatVerification resultat = journal.verifier(null);

    assertThat(resultat.intact()).isTrue();
    assertThat(resultat.nonScellees()).isEqualTo(1);
    assertThat(resultat.verifiees()).isEqualTo(3);
  }

  @Test
  @DisplayName("une vérification bornée s'arrête au nombre d'entrées demandé")
  void verifier_bornee() {
    simulerJournal(chaineDe(10));

    ResultatVerification resultat = journal.verifier(4L);

    assertThat(resultat.intact()).isTrue();
    assertThat(resultat.verifiees()).isEqualTo(4);
  }

  @Test
  @DisplayName("un journal vide est intact")
  void verifier_journalVide() {
    simulerJournal(List.of());

    ResultatVerification resultat = journal.verifier(null);

    assertThat(resultat.intact()).isTrue();
    assertThat(resultat.verifiees()).isZero();
  }

  @Test
  @DisplayName("une écriture sans acteur est refusée à la construction")
  void ecriture_exigeUnActeur() {
    assertThat(
            org.assertj.core.api.Assertions.catchThrowable(
                () ->
                    JournalAudit.EcritureAudit.succes(
                        "+237699000001", "KYC_VERIFIED", "  ", TypeActeur.CLIENT, "")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("un journal plus grand qu'un lot est parcouru page à page")
  void verifier_franchitLesPages() {
    // Au-delà de la taille d'un lot, la vérification doit enchaîner les pages
    // par pagination sur clé. Sans ce cas, le franchissement de page ne serait
    // jamais exercé et une erreur de position resterait invisible.
    simulerJournal(chaineDe(2_050));

    ResultatVerification resultat = journal.verifier(null);

    assertThat(resultat.intact()).isTrue();
    assertThat(resultat.verifiees()).isEqualTo(2_050);
  }

  @Test
  @DisplayName("une falsification au-delà de la première page est détectée")
  void verifier_falsificationSurLaSecondePage() {
    List<AuditLogEntry> entrees = chaineDe(2_050);
    AuditLogEntry cible = entrees.get(2_040);
    entrees.set(2_040, copieAvecDetails(cible, "retouche tardive"));
    simulerJournal(entrees);

    ResultatVerification resultat = journal.verifier(null);

    assertThat(resultat.intact()).isFalse();
    assertThat(resultat.rupture().identifiant()).isEqualTo(cible.getId());
    assertThat(resultat.rupture().motif())
        .isEqualTo(ResultatVerification.MotifRupture.CONTENU_MODIFIE);
  }

  @Test
  @DisplayName("une écriture en échec de base ne propage pas l'erreur mais retourne faux")
  void enregistrer_echecDeBase_neRemontePas() {
    when(depot.findFirstByOrderByTimestampDescIdDesc())
        .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("base absente"));

    boolean ecrit =
        journal.enregistrer(
            JournalAudit.EcritureAudit.succes(
                "+237699000001", "KYC_VERIFIED", "+237699000001", TypeActeur.CLIENT, "essai"));

    // L'opération métier ne doit pas tomber parce que sa trace a échoué.
    assertThat(ecrit).isFalse();
  }

  // ── Fabrication d'un journal cohérent ────────────────────────

  /** Construit une chaîne de n entrées correctement scellées. */
  private List<AuditLogEntry> chaineDe(int n) {
    List<AuditLogEntry> entrees = new ArrayList<>();
    String maillon = EntreeAudit.GENESE;
    for (int i = 0; i < n; i++) {
      AuditLogEntry e = entree(i, maillon, null);
      String empreinte = scelleur.empreinte(e.versDomaine());
      e.sceller(empreinte);
      maillon = empreinte;
      entrees.add(e);
    }
    return entrees;
  }

  private AuditLogEntry entree(int rang, String maillon, String empreinteForcee) {
    AuditLogEntry e =
        AuditLogEntry.builder()
            .id(UUID.nameUUIDFromBytes(("entree-" + rang).getBytes(StandardCharsets.UTF_8)))
            .timestamp(
                Instant.parse("2026-08-06T10:00:00Z")
                    .plusSeconds(rang)
                    .truncatedTo(ChronoUnit.MICROS))
            .phone("+23769900000" + rang)
            .eventType("KYC_VERIFIED")
            .status("SUCCESS")
            .actor("+23769900000" + rang)
            .actorType(TypeActeur.CLIENT)
            .sourceIp("10.0.0." + rang)
            .details("entrée " + rang)
            .prevHash(maillon)
            .build();
    if (empreinteForcee != null) {
      e.sceller(empreinteForcee);
    }
    return e;
  }

  /** Copie une entrée en changeant son contenu mais en conservant son empreinte. */
  private AuditLogEntry copieAvecDetails(AuditLogEntry origine, String details) {
    AuditLogEntry copie =
        AuditLogEntry.builder()
            .id(origine.getId())
            .timestamp(origine.getTimestamp())
            .phone(origine.getPhone())
            .eventType(origine.getEventType())
            .status(origine.getStatus())
            .actor(origine.getActor())
            .actorType(origine.getActorType())
            .sourceIp(origine.getSourceIp())
            .amount(origine.getAmount())
            .currency(origine.getCurrency())
            .reference(origine.getReference())
            .details(details)
            .prevHash(origine.getPrevHash())
            .build();
    copie.sceller(origine.getEntryHash());
    return copie;
  }

  /** Fait répondre le dépôt simulé comme le ferait la pagination par clé. */
  private void simulerJournal(List<AuditLogEntry> entrees) {
    when(depot.premierePage(any(Limit.class)))
        .thenAnswer(
            invocation -> {
              Limit limite = invocation.getArgument(0);
              return entrees.subList(0, Math.min(limite.max(), entrees.size()));
            });

    when(depot.pageApres(any(), any(), any(Limit.class)))
        .thenAnswer(
            invocation -> {
              Instant depuis = invocation.getArgument(0);
              UUID apres = invocation.getArgument(1);
              Limit limite = invocation.getArgument(2);
              List<AuditLogEntry> suite = new ArrayList<>();
              boolean atteint = false;
              for (AuditLogEntry e : entrees) {
                if (atteint) {
                  suite.add(e);
                  if (suite.size() >= limite.max()) {
                    break;
                  }
                } else if (e.getTimestamp().equals(depuis) && e.getId().equals(apres)) {
                  atteint = true;
                }
              }
              return suite;
            });

    when(depot.findFirstByOrderByTimestampDescIdDesc())
        .thenReturn(entrees.isEmpty() ? Optional.empty() : Optional.of(entrees.getLast()));
  }
}
