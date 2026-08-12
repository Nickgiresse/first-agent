package com.firstagent.backend.common.enums;

/**
 * État du dossier d'un client, et donc de son accès au service.
 *
 * <p>Le statut répond à une seule question : ce client peut-il utiliser le service aujourd'hui ?
 * {@link #estActif()} la tranche, plutôt que de laisser chaque appelant énumérer les cas et oublier
 * celui qu'on ajoutera demain.
 */
public enum CustomerStatus {

  /**
   * Dossier validé, accès ouvert.
   *
   * <p>Nom conservé plutôt que renommé en {@code ACTIVE} : il est écrit tel quel dans la base et
   * dans le référentiel du WhatsApp banking, et le renommer imposerait une migration pour un gain
   * purement cosmétique.
   */
  USER,

  /**
   * Dossier complet mais non validé : l'accès n'est pas encore ouvert.
   *
   * <p>C'est l'état d'un dossier parti en révision, faute d'une pièce lisible ou d'une ressemblance
   * faciale assez nette. Il ne s'agit ni d'un refus ni d'une sanction : le client a tout fourni, et
   * un conseiller doit confirmer son identité avant l'activation.
   *
   * <p>Sans cet état, un dossier en attente était enregistré comme utilisateur ordinaire : le
   * drapeau de révision existait, mais rien n'empêchait l'accès entre-temps.
   */
  PENDING_REVIEW,

  /** Accès retiré à la suite d'un incident de sécurité. */
  BLOCKED,

  /** Accès suspendu temporairement, sur décision de gestion. */
  SUSPENDED;

  /**
   * Le client peut-il utiliser le service ?
   *
   * <p>Une seule définition, ici, plutôt qu'un {@code == USER} recopié partout : le jour où un état
   * s'ajoute, les appelants n'ont pas à être retrouvés un à un.
   */
  public boolean estActif() {
    return this == USER;
  }
}
