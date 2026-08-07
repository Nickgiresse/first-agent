package com.firstagent.backend.audit.model;

/**
 * Nature de l'auteur d'une entrée du journal.
 *
 * <p>Répond à « qui a fait cette opération », question à laquelle le seul numéro du client concerné
 * ne répond pas : une action venue du back-office porte le numéro du client mais n'est pas son
 * geste.
 */
public enum TypeActeur {

  /** Le client lui-même, depuis son parcours. */
  CLIENT,

  /** Un conseiller ou un administrateur, depuis le back-office. */
  ADMIN,

  /** Un traitement automatique, sans intervention humaine. */
  SYSTEM
}
