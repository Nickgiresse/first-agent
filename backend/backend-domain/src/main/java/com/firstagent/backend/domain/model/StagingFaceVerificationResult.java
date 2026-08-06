package com.firstagent.backend.domain.model;

import com.firstagent.backend.domain.model.valueobject.FaceVerificationResultId;
import com.firstagent.backend.domain.model.valueobject.FaceVerificationStatus;
import com.firstagent.backend.domain.model.valueobject.OnboardingSessionId;
import java.time.LocalDateTime;
import java.util.Objects;

public final class StagingFaceVerificationResult {
    private final FaceVerificationResultId id;
    private final OnboardingSessionId onboardingSessionId;
    private final Double similarityScore;
    private final Double targetQualityScore;
    private final String provider;
    private final FaceVerificationStatus status;
    private final LocalDateTime verifiedAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private StagingFaceVerificationResult(
            FaceVerificationResultId id,
            OnboardingSessionId onboardingSessionId,
            Double similarityScore,
            Double targetQualityScore,
            String provider,
            FaceVerificationStatus status,
            LocalDateTime verifiedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.onboardingSessionId = Objects.requireNonNull(onboardingSessionId);
        this.similarityScore = similarityScore;
        this.targetQualityScore = targetQualityScore;
        this.provider = Objects.requireNonNull(provider);
        this.status = Objects.requireNonNull(status);
        this.verifiedAt = verifiedAt;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static StagingFaceVerificationResult creer(
            OnboardingSessionId onboardingSessionId,
            Double similarityScore,
            Double targetQualityScore,
            String provider) {
        LocalDateTime now = LocalDateTime.now();
        return new StagingFaceVerificationResult(
                FaceVerificationResultId.generate(),
                onboardingSessionId,
                similarityScore,
                targetQualityScore,
                provider,
                FaceVerificationStatus.PENDING,
                now,
                now,
                now
        );
    }

    public static StagingFaceVerificationResult reconstituer(
            FaceVerificationResultId id,
            OnboardingSessionId onboardingSessionId,
            Double similarityScore,
            Double targetQualityScore,
            String provider,
            FaceVerificationStatus status,
            LocalDateTime verifiedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        return new StagingFaceVerificationResult(
                id,
                onboardingSessionId,
                similarityScore,
                targetQualityScore,
                provider,
                status,
                verifiedAt,
                createdAt,
                updatedAt
        );
    }

    public FaceVerificationResultId getId() {
        return id;
    }

    public OnboardingSessionId getOnboardingSessionId() {
        return onboardingSessionId;
    }

    public Double getSimilarityScore() {
        return similarityScore;
    }

    public Double getTargetQualityScore() {
        return targetQualityScore;
    }

    public String getProvider() {
        return provider;
    }

    public FaceVerificationStatus getStatus() {
        return status;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
