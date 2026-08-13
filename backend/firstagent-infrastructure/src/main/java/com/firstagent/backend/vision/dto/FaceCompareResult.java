package com.firstagent.backend.vision.dto;

/**
 * Réponse de comparaison biométrique du microservice Python.
 *
 * <p>{@code liveness} n'est renseigné que si un identifiant de session de vivacité a été transmis.
 * Son absence n'est pas une réussite : elle signifie que le selfie comparé n'a PAS pu être rattaché
 * à la personne qui a joué le défi.
 */
public record FaceCompareResult(
    double similarityScore,
    String decision,
    double threshold,
    boolean sourceFaceDetected,
    boolean targetFaceDetected,
    LivenessBinding liveness) {

  public boolean matched() {
    return "MATCH".equals(decision);
  }

  /** Le selfie comparé est-il, de façon établie, le visage qui a joué le défi ? */
  public boolean lieAuDefi() {
    return liveness != null && liveness.bound() && Boolean.TRUE.equals(liveness.samePerson());
  }

  public record LivenessBinding(
      boolean bound, Boolean samePerson, Double similarity, Double threshold, String reason) {}
}
