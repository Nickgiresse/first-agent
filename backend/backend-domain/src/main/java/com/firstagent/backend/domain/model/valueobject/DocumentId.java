package com.firstagent.backend.domain.model.valueobject;

import java.util.Objects;
import java.util.UUID;

public record DocumentId(UUID value) {
    public DocumentId {
        Objects.requireNonNull(value, "DocumentId value cannot be null");
    }

    public static DocumentId generate() {
        return new DocumentId(UuidV7Generator.generate());
    }
}
