package com.firstagent.backend.domain.model;

import com.firstagent.backend.domain.model.valueobject.CustomerId;
import com.firstagent.backend.domain.model.valueobject.LivenessResultId;
import com.firstagent.backend.domain.model.valueobject.LivenessStatus;
import java.time.LocalDateTime;
import java.util.Objects;

public final class LivenessResult {
    private final LivenessResultId id;
    private final CustomerId customerId;
    private final String sessionId;
    private final LivenessStatus status;
    private final String completedActions;
    private final Integer totalActions;
    private final LocalDateTime verifiedAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private LivenessResult(
            LivenessResultId id,
            CustomerId customerId,
            String sessionId,
            LivenessStatus status,
            String completedActions,
            Integer totalActions,
            LocalDateTime verifiedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.customerId = Objects.requireNonNull(customerId);
        this.sessionId = Objects.requireNonNull(sessionId);
        this.status = Objects.requireNonNull(status);
        this.completedActions = completedActions;
        this.totalActions = totalActions;
        this.verifiedAt = verifiedAt;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static LivenessResult creer(
            CustomerId customerId,
            String sessionId,
            Integer totalActions) {
        LocalDateTime now = LocalDateTime.now();
        return new LivenessResult(
                LivenessResultId.generate(),
                customerId,
                sessionId,
                LivenessStatus.PENDING,
                "",
                totalActions,
                now,
                now,
                now
        );
    }

    public static LivenessResult reconstituer(
            LivenessResultId id,
            CustomerId customerId,
            String sessionId,
            LivenessStatus status,
            String completedActions,
            Integer totalActions,
            LocalDateTime verifiedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        return new LivenessResult(
                id,
                customerId,
                sessionId,
                status,
                completedActions,
                totalActions,
                verifiedAt,
                createdAt,
                updatedAt
        );
    }

    public LivenessResultId getId() {
        return id;
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public LivenessStatus getStatus() {
        return status;
    }

    public String getCompletedActions() {
        return completedActions;
    }

    public Integer getTotalActions() {
        return totalActions;
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
