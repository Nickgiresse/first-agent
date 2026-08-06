package com.firstagent.backend.common.exception;

/**
 * Nature d'une erreur métier, exprimée sans référence au transport.
 *
 * <p>Le domaine sait qu'une opération est refusée parce qu'une règle n'est pas
 * remplie, parce qu'une ressource est introuvable ou parce qu'un état entre en
 * conflit. Il n'a pas à savoir que cela se traduit par un 422, un 404 ou un
 * 409 : la correspondance vers un code HTTP appartient à l'adaptateur web, et
 * elle est faite une seule fois, dans le gestionnaire d'exceptions.
 *
 * <p>C'est ce qui permet au module domain de rester pur, comme l'exige la
 * charte backend §3 : auparavant chaque exception portait un
 * {@code HttpStatus}, donc une dépendance à Spring jusqu'au cœur du métier.
 *
 * <p>La correspondance appliquée figure dans {@code GlobalExceptionHandler} et
 * suit le tableau de la charte §6.
 */
public enum TypeErreurMetier {

    /** Donnée reçue mal formée ou hors bornes. */
    VALIDATION,

    /** Règle de gestion non satisfaite alors que la donnée est bien formée. */
    REGLE_METIER,

    /** Ressource demandée inexistante. */
    INTROUVABLE,

    /** État incompatible : unicité rompue, double soumission, verrou optimiste. */
    CONFLIT,

    /** Identité non établie ou preuve invalide. */
    NON_AUTORISE,

    /** Identité établie mais droit absent sur cette ressource. */
    INTERDIT,

    /** Ressource ayant existé et dont la validité est écoulée. */
    EXPIRE,

    /** Défaillance technique interne, sans faute de l'appelant. */
    SYSTEME
}
