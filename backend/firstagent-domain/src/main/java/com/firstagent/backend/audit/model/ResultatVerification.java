package com.firstagent.backend.audit.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Issue d'une vérification de la chaîne du journal.
 *
 * @param intact vrai tant qu'aucune rupture n'a été rencontrée
 * @param verifiees nombre d'entrées scellées dont la signature a été recalculée et confirmée
 * @param nonScellees entrées antérieures au scellement : elles ne rompent pas la chaîne mais ne
 *     sont couvertes par aucune garantie, et sont comptées à part pour que ce soit visible
 * @param rupture première anomalie rencontrée, ou {@code null} si la chaîne est intacte
 */
public record ResultatVerification(
    boolean intact, long verifiees, long nonScellees, Rupture rupture) {

  /** Chaîne intacte sur l'étendue parcourue. */
  public static ResultatVerification intacte(long verifiees, long nonScellees) {
    return new ResultatVerification(true, verifiees, nonScellees, null);
  }

  /** Chaîne rompue : la vérification s'arrête à la première anomalie. */
  public static ResultatVerification rompue(long verifiees, long nonScellees, Rupture rupture) {
    return new ResultatVerification(false, verifiees, nonScellees, rupture);
  }

  /**
   * Première anomalie rencontrée.
   *
   * @param identifiant entrée où la vérification s'est arrêtée
   * @param horodatage instant de cette entrée
   * @param typeEvenement nature de l'événement concerné
   * @param motif ce qui a été constaté, en clair
   * @param attendu valeur que le recalcul donnait
   * @param trouve valeur effectivement enregistrée
   */
  public record Rupture(
      UUID identifiant,
      Instant horodatage,
      String typeEvenement,
      MotifRupture motif,
      String attendu,
      String trouve) {}

  /** Ce que la rupture démontre. */
  public enum MotifRupture {

    /**
     * Le maillon ne correspond pas : une entrée a été supprimée, insérée ou réordonnée avant
     * celle-ci.
     */
    CHAINAGE_ROMPU,

    /**
     * La signature ne correspond plus au contenu : l'entrée a été modifiée après enregistrement.
     */
    CONTENU_MODIFIE
  }
}
