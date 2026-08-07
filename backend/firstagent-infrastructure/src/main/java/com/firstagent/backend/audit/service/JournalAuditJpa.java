package com.firstagent.backend.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.firstagent.backend.audit.entity.AuditLogEntry;
import com.firstagent.backend.audit.model.EntreeAudit;
import com.firstagent.backend.audit.model.ResultatVerification;
import com.firstagent.backend.audit.model.ScelleurAudit;
import com.firstagent.backend.audit.port.JournalAudit;
import com.firstagent.backend.audit.repository.AuditLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Journal forensique adossé à la base relationnelle. */
@Slf4j
@Service
public class JournalAuditJpa implements JournalAudit {

  /** Nombre d'entrées relues par page lors d'une vérification. */
  private static final int TAILLE_LOT = 2_000;

  private final AuditLogRepository depot;
  private final ScelleurAudit scelleur;

  /**
   * Sérialiseur propre à l'export, délibérément pas celui de l'application.
   *
   * <p>Le format attendu par un collecteur SIEM est un contrat avec un tiers. L'adosser au {@code
   * ObjectMapper} du web le rendrait sensible à n'importe quel réglage de sérialisation fait pour
   * les besoins de l'API : un jour où quelqu'un change une convention de nommage ou l'inclusion des
   * valeurs nulles, l'ingestion du SIEM casserait sans que personne ne fasse le lien.
   */
  private final ObjectMapper json = new ObjectMapper();

  /**
   * Sérialise les écritures du processus.
   *
   * <p>Le chaînage lit la dernière empreinte puis écrit : deux écritures simultanées bâtiraient
   * deux entrées sur le même maillon, et la chaîne serait rompue par construction.
   *
   * <p>LIMITE ASSUMÉE. Ce verrou ne porte que sur une instance. Dès qu'un second exemplaire du
   * service écrira dans la même base, il faudra un verrou porté par elle, du type {@code SELECT ...
   * FOR UPDATE} sur le dernier maillon. C'est écrit ici plutôt que découvert le jour de la mise à
   * l'échelle.
   */
  private final ReentrantLock verrouChaine = new ReentrantLock();

  public JournalAuditJpa(AuditLogRepository depot, ScelleurAudit scelleur) {
    this.depot = depot;
    this.scelleur = scelleur;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Transaction propre ({@code REQUIRES_NEW}) : la trace doit survivre à l'échec de l'opération
   * qu'elle relate. Journaliser un refus dans la transaction refusée reviendrait à effacer la trace
   * en même temps que la cause.
   */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean enregistrer(EcritureAudit ecriture) {
    verrouChaine.lock();
    try {
      String maillonPrecedent =
          depot
              .findFirstByOrderByTimestampDescIdDesc()
              .map(AuditLogEntry::getEntryHash)
              .filter(h -> h != null && !h.isBlank())
              .orElse(EntreeAudit.GENESE);

      AuditLogEntry entree =
          AuditLogEntry.builder()
              .phone(ecriture.telephone())
              .eventType(ecriture.typeEvenement())
              .status(ecriture.statut())
              .actor(ecriture.acteur())
              .actorType(ecriture.typeActeur())
              .sourceIp(ecriture.ipSource())
              .amount(ecriture.montant())
              .currency(ecriture.devise())
              .reference(ecriture.reference())
              .details(ecriture.details())
              .prevHash(maillonPrecedent)
              .build();

      // Matérialiser l'identifiant et l'horodatage AVANT de sceller : sans ce
      // vidage, l'empreinte porterait sur des champs encore vides et aucune
      // vérification ultérieure ne la retrouverait.
      depot.saveAndFlush(entree);
      entree.sceller(scelleur.empreinte(entree.versDomaine()));
      depot.saveAndFlush(entree);
      return true;

    } catch (RuntimeException e) {
      // Une opération métier ne doit pas échouer parce que sa journalisation a
      // échoué. Mais une trace perdue est un incident de conformité : elle est
      // signalée au niveau le plus grave, pas noyée dans un avertissement.
      log.error(
          "[AUDIT] ÉCRITURE PERDUE {} / {} par {} : {}",
          ecriture.telephone(),
          ecriture.typeEvenement(),
          ecriture.acteur(),
          e.getMessage(),
          e);
      return false;
    } finally {
      verrouChaine.unlock();
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>MÉMOIRE BORNÉE. Le journal est parcouru page à page, jamais chargé en entier. L'enjeu n'est
   * pas théorique : à quelques kilo-octets par entrée hydratée, un chargement global demanderait
   * plusieurs dizaines de gigaoctets sur un journal de quelques dizaines de millions de lignes,
   * volume atteint en moins d'un an sur une base de 800 000 clients.
   *
   * <p>Le contexte de persistance est vidé à chaque page : sans cela, il conserverait chaque entité
   * hydratée et la pagination ne bornerait rien.
   */
  @Override
  @Transactional(readOnly = true)
  public ResultatVerification verifier(Long limite) {
    String attendu = EntreeAudit.GENESE;
    long verifiees = 0;
    long nonScellees = 0;
    Instant positionHorodatage = null;
    java.util.UUID positionId = null;
    Long restant = limite;

    while (true) {
      int taille = restant == null ? TAILLE_LOT : (int) Math.min(TAILLE_LOT, restant);
      if (taille <= 0) {
        break;
      }

      List<AuditLogEntry> page =
          positionHorodatage == null
              ? depot.premierePage(Limit.of(taille))
              : depot.pageApres(positionHorodatage, positionId, Limit.of(taille));
      if (page.isEmpty()) {
        break;
      }

      for (AuditLogEntry entree : page) {
        String stocke = entree.getEntryHash();
        if (stocke == null || stocke.isBlank()) {
          // Entrée antérieure au scellement : elle ne rompt pas la chaîne mais
          // n'est couverte par aucune garantie. Comptée à part pour que ce soit
          // visible plutôt que silencieux.
          nonScellees++;
          continue;
        }

        if (!attendu.equals(entree.getPrevHash())) {
          return ResultatVerification.rompue(
              verifiees,
              nonScellees,
              rupture(
                  entree,
                  ResultatVerification.MotifRupture.CHAINAGE_ROMPU,
                  attendu,
                  entree.getPrevHash()));
        }

        String recalcule = scelleur.empreinte(entree.versDomaine());
        if (!scelleur.correspond(recalcule, stocke)) {
          return ResultatVerification.rompue(
              verifiees,
              nonScellees,
              rupture(
                  entree, ResultatVerification.MotifRupture.CONTENU_MODIFIE, recalcule, stocke));
        }

        attendu = stocke;
        verifiees++;
      }

      AuditLogEntry derniere = page.get(page.size() - 1);
      positionHorodatage = derniere.getTimestamp();
      positionId = derniere.getId();
      if (restant != null) {
        restant -= page.size();
      }
    }

    return ResultatVerification.intacte(verifiees, nonScellees);
  }

  private ResultatVerification.Rupture rupture(
      AuditLogEntry entree,
      ResultatVerification.MotifRupture motif,
      String attendu,
      String trouve) {
    return new ResultatVerification.Rupture(
        entree.getId(), entree.getTimestamp(), entree.getEventType(), motif, attendu, trouve);
  }

  @Override
  @Transactional(readOnly = true)
  public Stream<String> exporterJsonl(Instant depuis, Long limite) {
    int taille = limite == null ? Integer.MAX_VALUE : (int) Math.min(Integer.MAX_VALUE, limite);
    Limit borne = Limit.of(taille);
    List<AuditLogEntry> entrees = depuis == null ? depot.tout(borne) : depot.depuis(depuis, borne);
    return entrees.stream().map(this::versJsonl);
  }

  /**
   * Une entrée au format JSON Lines, structuré selon le schéma commun ECS.
   *
   * <p>Les empreintes sont incluses pour que le collecteur puisse rejouer la vérification de son
   * côté, sans avoir à interroger la base.
   */
  private String versJsonl(AuditLogEntry e) {
    ObjectNode racine = json.createObjectNode();
    racine.put("@timestamp", e.getTimestamp() == null ? null : e.getTimestamp().toString());

    ObjectNode evenement = racine.putObject("event");
    evenement.put("kind", "event");
    evenement.put("category", "authentication");
    evenement.put("action", e.getEventType());
    evenement.put("outcome", e.getStatus());

    ObjectNode acteur = racine.putObject("actor");
    acteur.put("id", e.getActor());
    acteur.put("type", e.getActorType() == null ? null : e.getActorType().name());

    racine.putObject("client").put("phone", e.getPhone());
    racine.putObject("source").put("ip", vide(e.getSourceIp()) ? null : e.getSourceIp());

    ObjectNode operation = racine.putObject("transaction");
    operation.put("amount", e.getAmount());
    operation.put("currency", e.getCurrency());
    operation.put("reference", e.getReference());

    racine.put("message", e.getDetails());

    ObjectNode scelle = racine.putObject("audit");
    scelle.put("id", e.getId() == null ? null : e.getId().toString());
    scelle.put("entry_hash", e.getEntryHash());
    scelle.put("prev_hash", e.getPrevHash());

    return racine.toString();
  }

  private boolean vide(String valeur) {
    return valeur == null || valeur.isBlank();
  }
}
