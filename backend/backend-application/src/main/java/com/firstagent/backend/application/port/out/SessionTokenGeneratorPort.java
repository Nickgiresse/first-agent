package com.firstagent.backend.application.port.out;

import com.firstagent.backend.domain.model.valueobject.OnboardingSessionToken;

public interface SessionTokenGeneratorPort {
    OnboardingSessionToken generate();
}
