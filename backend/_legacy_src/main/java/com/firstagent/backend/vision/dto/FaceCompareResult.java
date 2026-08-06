package com.firstagent.backend.vision.dto;

public record FaceCompareResult(
    double similarityScore,
    String decision,
    double threshold,
    boolean sourceFaceDetected,
    boolean targetFaceDetected
) {
    public boolean matched() {
        return "MATCH".equals(decision);
    }
}
