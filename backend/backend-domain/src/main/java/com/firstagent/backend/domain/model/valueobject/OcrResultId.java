package com.firstagent.backend.domain.model.valueobject;

import java.util.Objects;
import java.util.UUID;

public record OcrResultId(UUID value) {
    public OcrResultId {
        Objects.requireNonNull(value, "OcrResultId value cannot be null");
    }

    public static OcrResultId generate() {
        return new OcrResultId(UuidV7Generator.generate());
    }
}
