package com.firstagent.backend.application.dto;

import java.time.LocalDateTime;

public record FaceVerificationResponse(
    String faceVerificationResultId,
    boolean matched,
    double similarityScore,
    Double targetQualityScore,
    String status,
    String provider,
    LocalDateTime verifiedAt
) {}
