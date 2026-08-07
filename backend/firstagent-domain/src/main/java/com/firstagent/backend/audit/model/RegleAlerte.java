package com.firstagent.backend.audit.model;

import java.time.Duration;
import java.util.List;

/**
 * Règle de détection d'un comportement suspect dans le journal.
 *
 * <p>Une règle dit : « tel type d'événement, en échec, répété tant de fois pour une même cible et
 * dans tel intervalle, mérite qu'on prévienne quelqu'un ». Rien de plus. Le seuil et la fenêtre
 * sont des paramètres parce qu'ils s'ajustent à l'usage : trop bas, l'alerte devient du bruit et
 * plus personne ne la lit ; trop haut, elle arrive après coup.
 *
 * @param code identifiant court de la règle, qui sert à dédupliquer les alertes
 * @param typesEvenements événements que la règle observe
 * @param fenetre intervalle sur lequel les occurrences sont comptées
 * @param seuil nombre d'occurrences à partir duquel la règle se déclenche
 * @param modeleMessage message d'alerte, où {@code %d} reçoit le compte et {@code %s} la cible
 */
public record RegleAlerte(
    String code, List<String> typesEvenements, Duration fenetre, int seuil, String modeleMessage) {

  public RegleAlerte {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Une règle d'alerte doit avoir un code.");
    }
    if (typesEvenements == null || typesEvenements.isEmpty()) {
      throw new IllegalArgumentException("Une règle d'alerte doit observer au moins un événement.");
    }
    typesEvenements = List.copyOf(typesEvenements);
  }

  /**
   * La règle est-elle active ?
   *
   * <p>Un seuil nul ou négatif la désactive. C'est délibérément plus simple qu'un drapeau séparé :
   * il n'existe qu'un seul endroit à régler, et « seuil zéro » ne peut pas vouloir dire « alerter à
   * chaque occurrence » par accident.
   */
  public boolean active() {
    return seuil > 0;
  }

  /** Se déclenche-t-elle pour ce nombre d'occurrences ? */
  public boolean declenche(long occurrences) {
    return active() && occurrences >= seuil;
  }

  /**
   * Référence de l'alerte pour une cible donnée.
   *
   * <p>C'est elle qui permet de ne pas réécrire la même alerte à chaque balayage : tant qu'une
   * alerte de même référence existe dans la fenêtre, la règle se tait.
   */
  public String reference(String cible) {
    return code + ":" + cible;
  }

  /** Message destiné à la personne qui recevra l'alerte. */
  public String message(long occurrences, String cible) {
    return String.format(modeleMessage, occurrences, cible);
  }
}
