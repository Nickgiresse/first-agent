package com.firstagent.backend.domain.model.valueobject;

import java.util.Objects;

public record OnboardingSessionToken(String value) {
    public OnboardingSessionToken {
        Objects.requireNonNull(value, "OnboardingSessionToken value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("OnboardingSessionToken value cannot be blank");
        }
    }
}
