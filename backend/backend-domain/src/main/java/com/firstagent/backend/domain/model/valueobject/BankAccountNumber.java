package com.firstagent.backend.domain.model.valueobject;

import java.util.Objects;

public record BankAccountNumber(String value) {
    public BankAccountNumber {
        Objects.requireNonNull(value, "BankAccountNumber value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("BankAccountNumber value cannot be blank");
        }
    }
}
