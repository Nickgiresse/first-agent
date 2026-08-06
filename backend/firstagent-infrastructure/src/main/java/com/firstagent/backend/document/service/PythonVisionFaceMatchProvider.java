package com.firstagent.backend.document.service;

import com.firstagent.backend.document.model.FaceMatchResult;
import com.firstagent.backend.document.port.FaceMatchProvider;
import com.firstagent.backend.vision.client.PythonVisionClient;
import com.firstagent.backend.vision.dto.FaceAnalyzeResult;
import com.firstagent.backend.vision.dto.FaceCompareResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Implémentation de {@link FaceMatchProvider} appelant le microservice Python (Module 3 : qualité
 * du selfie, puis Module 5 : comparaison biométrique). Remplace AWS Rekognition/CompreFace.
 */
@Service
@RequiredArgsConstructor
public class PythonVisionFaceMatchProvider implements FaceMatchProvider {

  private final PythonVisionClient visionClient;

  @Override
  public FaceMatchResult compareFaces(byte[] referenceImage, byte[] targetImage) {
    FaceAnalyzeResult targetQuality = visionClient.analyzeFace(targetImage);
    FaceCompareResult comparison = visionClient.compareFaces(referenceImage, targetImage);

    // similarityScore de Python = similarité cosinus (-1 à 1, typiquement 0 à 0.8 en pratique) ;
    // ×100 pour un affichage cohérent avec les anciens fournisseurs (0-100), pas une vraie
    // proportion bornée — la décision MATCH/NO_MATCH vient du seuil déjà appliqué côté Python.
    return new FaceMatchResult(
        comparison.matched(), comparison.similarityScore() * 100.0, targetQuality.qualityScore());
  }

  @Override
  public String getProviderName() {
    return "PYTHON_VISION";
  }
}
