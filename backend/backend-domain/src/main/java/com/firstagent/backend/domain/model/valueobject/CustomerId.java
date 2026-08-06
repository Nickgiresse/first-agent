package com.firstagent.backend.domain.model.valueobject;

import java.util.Objects;
import java.util.UUID;

public record CustomerId(UUID value) {
    public CustomerId {
        Objects.requireNonNull(value, "CustomerId value cannot be null");
    }

    public static CustomerId generate() {
        return new CustomerId(UuidV7Generator.generate());
    }
}
