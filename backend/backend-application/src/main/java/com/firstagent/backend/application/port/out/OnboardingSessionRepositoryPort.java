package com.firstagent.backend.application.port.out;

import com.firstagent.backend.domain.model.OnboardingSession;
import com.firstagent.backend.domain.model.valueobject.OnboardingSessionToken;
import reactor.core.publisher.Mono;

public interface OnboardingSessionRepositoryPort {
    Mono<OnboardingSession> findBySessionToken(OnboardingSessionToken token);
    Mono<OnboardingSession> save(OnboardingSession session);
}
