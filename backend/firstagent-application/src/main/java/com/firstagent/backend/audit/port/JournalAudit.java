package com.firstagent.backend.audit.port;

import com.firstagent.backend.audit.model.ResultatVerification;
import com.firstagent.backend.audit.model.TypeActeur;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * Journal forensique : ce que le métier attend d'une trace opposable.
 *
 * <p>Deux garanties, qui sont la raison d'être de ce port.
 *
 * <p><b>Imputabilité.</b> Chaque entrée porte un acteur explicite. Le seul numéro du client
 * concerné ne dit pas qui a agi : une opération passée depuis le back-office porte ce numéro sans
 * être le geste du client.
 *
 * <p><b>Intégrité démontrable.</b> Les entrées forment une chaîne scellée, et {@link #verifier}
 * sait désigner la première rupture.
 */
public interface JournalAudit {

  /**
   * Ajoute une entrée scellée au journal.
   *
   * <p>Ne lève jamais : une opération métier ne doit pas échouer parce que sa journalisation a
   * échoué. Le retour dit si la trace a bien été écrite, et une écriture perdue est un incident de
   * conformité que l'implémentation signale au niveau le plus grave.
   *
   * @return vrai si l'entrée a été enregistrée
   */
  boolean enregistrer(EcritureAudit ecriture);

  /**
   * Recalcule la chaîne et désigne la première rupture.
   *
   * @param limite nombre maximal d'entrées à parcourir, ou {@code null} pour tout vérifier. Sur un
   *     journal volumineux, un contrôle de routine a intérêt à se borner : le temps de parcours
   *     reste proportionnel au volume.
   */
  ResultatVerification verifier(Long limite);

  /**
   * Produit le journal en JSON Lines, format ingérable par un SIEM.
   *
   * <p>Un objet par ligne, horodatage ISO 8601 en UTC, empreintes incluses pour que le collecteur
   * puisse rejouer la vérification de son côté.
   *
   * <p>Le flux doit être fermé par l'appelant : il tient une connexion ouverte le temps du
   * parcours.
   */
  Stream<String> exporterJsonl(Instant depuis, Long limite);

  /**
   * Contenu d'une entrée à journaliser.
   *
   * <p>{@code acteur} est obligatoire : c'est la réponse à « qui a fait cette opération ». Pour un
   * geste du client, son numéro ; pour une action du back-office, l'identifiant du conseiller ;
   * pour un traitement automatique, {@code system}.
   */
  record EcritureAudit(
      String telephone,
      String typeEvenement,
      String statut,
      String acteur,
      TypeActeur typeActeur,
      String ipSource,
      BigDecimal montant,
      String devise,
      String reference,
      String details) {

    private static final String SUCCES = "SUCCESS";
    private static final String ECHEC = "FAILURE";

    public EcritureAudit {
      if (acteur == null || acteur.isBlank()) {
        // Refuser plutôt qu'écrire une entrée sans auteur : un journal qui ne
        // désigne personne ne remplit pas son office.
        throw new IllegalArgumentException("Le journal exige un acteur : qui a fait l'opération ?");
      }
      if (typeEvenement == null || typeEvenement.isBlank()) {
        throw new IllegalArgumentException("Le journal exige un type d'événement.");
      }
      typeActeur = typeActeur == null ? TypeActeur.SYSTEM : typeActeur;
      statut = statut == null || statut.isBlank() ? SUCCES : statut;
    }

    /** Événement abouti, sans dimension financière. */
    public static EcritureAudit succes(
        String telephone,
        String typeEvenement,
        String acteur,
        TypeActeur typeActeur,
        String details) {
      return new EcritureAudit(
          telephone, typeEvenement, SUCCES, acteur, typeActeur, null, null, null, null, details);
    }

    /** Événement en échec : tentative refusée, contrôle non passé, opération abandonnée. */
    public static EcritureAudit echec(
        String telephone,
        String typeEvenement,
        String acteur,
        TypeActeur typeActeur,
        String details) {
      return new EcritureAudit(
          telephone, typeEvenement, ECHEC, acteur, typeActeur, null, null, null, null, details);
    }
  }
}
