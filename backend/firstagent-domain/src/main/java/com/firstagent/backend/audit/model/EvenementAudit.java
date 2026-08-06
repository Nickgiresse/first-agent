package com.firstagent.backend.audit.model;

/**
 * Vocabulaire des événements journalisés.
 *
 * <p>Des constantes plutôt que des chaînes écrites à chaque appel : une faute de frappe dans un
 * type d'événement ne casse rien visiblement, elle rend simplement l'événement introuvable par les
 * règles d'alerte et par les recherches du back-office. Le défaut ne se manifeste alors que le jour
 * où l'on cherche la trace, c'est-à-dire trop tard.
 *
 * <p>Les valeurs reprennent celles du bot WhatsApp, afin qu'un même incident porte le même nom des
 * deux côtés et qu'un SIEM puisse les corréler.
 */
public final class EvenementAudit {

  private EvenementAudit() {}

  /**
   * L'identité lue sur le document ne correspond pas au titulaire du compte.
   *
   * <p>Signal d'usurpation le plus direct du parcours : quelqu'un présente une pièce d'identité qui
   * n'est pas celle du titulaire du compte qu'il prétend activer.
   */
  public static final String CONTROLE_KYC = "KYC_CHECK";

  /** Code à usage unique erroné. */
  public static final String OTP_ERRONE = "OTP_FAIL";

  /** Trop de codes erronés : la vérification est verrouillée. */
  public static final String OTP_VERROUILLE = "OTP_LOCKED";

  /** Vérification de vivacité non passée. */
  public static final String VIVACITE_ECHOUEE = "LIVENESS_FAIL";

  /** Comparaison faciale sous le seuil d'acceptation. */
  public static final String FACIAL_REFUSE = "FACE_MATCH_FAIL";

  /** Parcours d'activation mené à son terme. */
  public static final String ONBOARDING_ABOUTI = "ONBOARDING_COMPLETED";

  /** Parcours interrompu par un contrôle. */
  public static final String ONBOARDING_BLOQUE = "ONBOARDING_BLOCKED";

  /** Comportement suspect détecté par une règle d'alerte. */
  public static final String ALERTE_SECURITE = "SECURITY_ALERT";
}
