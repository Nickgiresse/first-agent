package com.firstagent.backend.domain.model.valueobject;

import java.util.Objects;
import java.util.UUID;

public record PinResetTokenId(UUID value) {
    public PinResetTokenId {
        Objects.requireNonNull(value, "PinResetTokenId value cannot be null");
    }

    public static PinResetTokenId generate() {
        return new PinResetTokenId(UuidV7Generator.generate());
    }
}
