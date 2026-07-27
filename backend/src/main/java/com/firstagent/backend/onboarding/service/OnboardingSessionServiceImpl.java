package com.firstagent.backend.onboarding.service;

import com.firstagent.backend.common.enums.OnboardingStatus;
import com.firstagent.backend.common.exception.OnboardingSessionExpiredException;
import com.firstagent.backend.common.exception.ResourceNotFoundException;
import com.firstagent.backend.onboarding.entity.OnboardingSession;
import com.firstagent.backend.onboarding.repository.OnboardingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OnboardingSessionServiceImpl implements OnboardingSessionService {

    private final OnboardingSessionRepository onboardingSessionRepository;

    @Override
    public OnboardingSession getValidSession(String sessionToken) {
        OnboardingSession session = onboardingSessionRepository.findBySessionToken(sessionToken)
            .orElseThrow(() -> new ResourceNotFoundException("Session d'onboarding introuvable"));

        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new OnboardingSessionExpiredException("La session d'onboarding a expiré, veuillez recommencer");
        }

        return session;
    }

    @Override
    @Transactional
    public void updateStatus(OnboardingSession session, OnboardingStatus newStatus) {
        session.setStatus(newStatus);
        onboardingSessionRepository.save(session);
    }
}