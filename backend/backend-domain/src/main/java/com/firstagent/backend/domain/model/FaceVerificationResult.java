package com.firstagent.backend.domain.model;

import com.firstagent.backend.domain.model.valueobject.CustomerId;
import com.firstagent.backend.domain.model.valueobject.FaceVerificationResultId;
import com.firstagent.backend.domain.model.valueobject.FaceVerificationStatus;
import java.time.LocalDateTime;
import java.util.Objects;

public final class FaceVerificationResult {
    private final FaceVerificationResultId id;
    private final CustomerId customerId;
    private final Double similarityScore;
    private final Double targetQualityScore;
    private final String provider;
    private final FaceVerificationStatus status;
    private final LocalDateTime verifiedAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private FaceVerificationResult(
            FaceVerificationResultId id,
            CustomerId customerId,
            Double similarityScore,
            Double targetQualityScore,
            String provider,
            FaceVerificationStatus status,
            LocalDateTime verifiedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.customerId = Objects.requireNonNull(customerId);
        this.similarityScore = similarityScore;
        this.targetQualityScore = targetQualityScore;
        this.provider = Objects.requireNonNull(provider);
        this.status = Objects.requireNonNull(status);
        this.verifiedAt = verifiedAt;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static FaceVerificationResult creer(
            CustomerId customerId,
            Double similarityScore,
            Double targetQualityScore,
            String provider) {
        LocalDateTime now = LocalDateTime.now();
        return new FaceVerificationResult(
                FaceVerificationResultId.generate(),
                customerId,
                similarityScore,
                targetQualityScore,
                provider,
                FaceVerificationStatus.PENDING,
                now,
                now,
                now
        );
    }

    public static FaceVerificationResult reconstituer(
            FaceVerificationResultId id,
            CustomerId customerId,
            Double similarityScore,
            Double targetQualityScore,
            String provider,
            FaceVerificationStatus status,
            LocalDateTime verifiedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        return new FaceVerificationResult(
                id,
                customerId,
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

    public CustomerId getCustomerId() {
        return customerId;
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
