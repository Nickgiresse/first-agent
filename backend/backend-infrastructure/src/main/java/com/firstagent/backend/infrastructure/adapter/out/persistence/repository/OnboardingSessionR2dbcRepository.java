package com.firstagent.backend.infrastructure.adapter.out.persistence.repository;

import com.firstagent.backend.infrastructure.adapter.out.persistence.entity.OnboardingSessionEntity;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface OnboardingSessionR2dbcRepository extends ReactiveCrudRepository<OnboardingSessionEntity, UUID> {
    Mono<OnboardingSessionEntity> findBySessionToken(String sessionToken);
}
