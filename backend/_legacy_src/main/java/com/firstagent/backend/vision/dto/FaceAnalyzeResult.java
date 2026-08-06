package com.firstagent.backend.vision.dto;

import java.util.List;

public record FaceAnalyzeResult(
    boolean faceDetected,
    int faceCount,
    boolean singleFace,
    boolean centered,
    boolean eyesDetected,
    boolean noseDetected,
    boolean mouthDetected,
    int qualityScore,
    List<String> issues
) {
}
