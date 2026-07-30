package com.firstagent.backend.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaceVerificationResponse {

    private UUID faceVerificationResultId;
    private boolean matched;
    private Double similarityScore;
    private Double targetQualityScore;
    private String status;
    private String provider;
    private LocalDateTime verifiedAt;
}
