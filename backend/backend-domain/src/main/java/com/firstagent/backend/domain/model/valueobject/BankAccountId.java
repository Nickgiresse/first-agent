package com.firstagent.backend.domain.model.valueobject;

import java.util.Objects;
import java.util.UUID;

public record BankAccountId(UUID value) {
    public BankAccountId {
        Objects.requireNonNull(value, "BankAccountId value cannot be null");
    }

    public static BankAccountId generate() {
        return new BankAccountId(UuidV7Generator.generate());
    }
}
