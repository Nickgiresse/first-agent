package com.firstagent.backend.domain.model;

import com.firstagent.backend.domain.model.valueobject.CustomerId;
import com.firstagent.backend.domain.model.valueobject.PinResetTokenId;
import java.time.LocalDateTime;
import java.util.Objects;

public final class PinResetToken {
    private final PinResetTokenId id;
    private final CustomerId customerId;
    private final String resetToken;
    private final LocalDateTime expiresAt;
    private final boolean used;
    private final LocalDateTime createdAt;

    private PinResetToken(
            PinResetTokenId id,
            CustomerId customerId,
            String resetToken,
            LocalDateTime expiresAt,
            boolean used,
            LocalDateTime createdAt) {
        this.id = Objects.requireNonNull(id);
        this.customerId = Objects.requireNonNull(customerId);
        this.resetToken = Objects.requireNonNull(resetToken);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.used = used;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static PinResetToken creer(
            CustomerId customerId,
            String resetToken,
            LocalDateTime expiresAt) {
        return new PinResetToken(
                PinResetTokenId.generate(),
                customerId,
                resetToken,
                expiresAt,
                false,
                LocalDateTime.now()
        );
    }

    public static PinResetToken reconstituer(
            PinResetTokenId id,
            CustomerId customerId,
            String resetToken,
            LocalDateTime expiresAt,
            boolean used,
            LocalDateTime createdAt) {
        return new PinResetToken(
                id,
                customerId,
                resetToken,
                expiresAt,
                used,
                createdAt
        );
    }

    public PinResetTokenId getId() {
        return id;
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    public String getResetToken() {
        return resetToken;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public boolean isUsed() {
        return used;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
