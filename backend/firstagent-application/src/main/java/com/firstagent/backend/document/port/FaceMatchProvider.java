package com.firstagent.backend.document.port;

import com.firstagent.backend.document.model.FaceMatchResult;

public interface FaceMatchProvider {

  /**
   * Compare la photo de la pièce d'identité au selfie.
   *
   * @param livenessSessionId session du défi de vivacité joué par le client, ou {@code null}. Sans
   *     lui, le fournisseur ne peut pas établir que le selfie soumis est celui de la personne qui a
   *     joué le défi : les deux preuves restent indépendantes, et rien n'impose qu'elles portent
   *     sur le même individu.
   */
  FaceMatchResult compareFaces(byte[] referenceImage, byte[] targetImage, String livenessSessionId);

  String getProviderName();
}
