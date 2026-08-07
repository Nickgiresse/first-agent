package com.firstagent.backend.audit.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Contenu scellé d'une entrée du journal.
 *
 * <p>C'est exactement ce que couvre la signature, ni plus ni moins. Le type existe pour que cette
 * frontière soit lisible : ajouter un champ à l'entité persistée sans l'ajouter ici le laisserait
 * hors de la garantie d'intégrité, en silence.
 *
 * <p>{@code empreintePrecedente} n'est pas une donnée de l'événement mais le maillon qui rattache
 * l'entrée à celle qui la précède. Il fait partie du scellé, et c'est ce qui transforme une suite
 * d'entrées signées en chaîne : sans lui, on pourrait supprimer ou réordonner des entrées sans
 * qu'aucune signature ne cesse d'être valide.
 *
 * @param identifiant identifiant de l'entrée, connu seulement après insertion
 * @param horodatage instant de l'événement, en UTC tronqué à la microseconde
 * @param telephone numéro du client concerné, éventuellement vide
 * @param typeEvenement nature de l'événement journalisé
 * @param statut issue de l'opération
 * @param acteur qui a agi : numéro du client, identifiant du conseiller, ou « system »
 * @param typeActeur à quel titre l'acteur a agi
 * @param ipSource adresse d'origine quand elle est connue
 * @param montant montant en jeu pour une opération financière
 * @param devise devise du montant
 * @param reference référence métier de l'opération
 * @param details texte libre décrivant l'événement
 * @param empreintePrecedente empreinte de l'entrée précédente, ou {@link #GENESE} pour la première
 */
public record EntreeAudit(
    UUID identifiant,
    Instant horodatage,
    String telephone,
    String typeEvenement,
    String statut,
    String acteur,
    TypeActeur typeActeur,
    String ipSource,
    BigDecimal montant,
    String devise,
    String reference,
    String details,
    String empreintePrecedente) {

  /**
   * Maillon d'origine de la chaîne.
   *
   * <p>Une valeur explicite plutôt qu'un {@code null} : celui-ci se confondrait avec « champ non
   * renseigné » et laisserait une ambiguïté au moment de vérifier.
   */
  public static final String GENESE = "GENESE";
}
