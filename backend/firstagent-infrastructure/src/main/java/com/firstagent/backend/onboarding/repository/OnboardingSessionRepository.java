package com.firstagent.backend.onboarding.repository;

import com.firstagent.backend.onboarding.entity.OnboardingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OnboardingSessionRepository extends JpaRepository<OnboardingSession, UUID> {

    Optional<OnboardingSession> findBySessionToken(String sessionToken);
}