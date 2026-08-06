package com.firstagent.backend.domain.model.valueobject;

import java.util.Objects;
import java.util.UUID;

public record FaceVerificationResultId(UUID value) {
    public FaceVerificationResultId {
        Objects.requireNonNull(value, "FaceVerificationResultId value cannot be null");
    }

    public static FaceVerificationResultId generate() {
        return new FaceVerificationResultId(UuidV7Generator.generate());
    }
}
