package com.firstagent.backend.document.model;

/**
 * Résultat d'une comparaison biométrique.
 *
 * @param livenessBound le selfie comparé est-il, de façon établie, le visage qui a joué le défi de
 *     vivacité ? {@code null} quand la question n'a pas été posée, faute de défi rattaché à la
 *     session. Ce n'est volontairement pas un booléen à deux états : « non lié » et « lié à
 *     quelqu'un d'autre » n'appellent pas le même traitement. Le second est refusé en amont par le
 *     microservice de vision, le premier relève de la revue conseiller.
 */
public record FaceMatchResult(
    boolean matched, double similarityScore, double targetQualityScore, Boolean livenessBound) {}
