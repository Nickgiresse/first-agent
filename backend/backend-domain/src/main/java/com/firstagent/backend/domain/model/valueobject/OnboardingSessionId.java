package com.firstagent.backend.domain.model.valueobject;

import java.util.Objects;
import java.util.UUID;

public record OnboardingSessionId(UUID value) {
    public OnboardingSessionId {
        Objects.requireNonNull(value, "OnboardingSessionId value cannot be null");
    }

    public static OnboardingSessionId generate() {
        return new OnboardingSessionId(UuidV7Generator.generate());
    }
}
