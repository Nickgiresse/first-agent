package com.firstagent.backend.domain.model.valueobject;

import java.util.Objects;
import java.util.UUID;

public record LivenessResultId(UUID value) {
    public LivenessResultId {
        Objects.requireNonNull(value, "LivenessResultId value cannot be null");
    }

    public static LivenessResultId generate() {
        return new LivenessResultId(UuidV7Generator.generate());
    }
}
