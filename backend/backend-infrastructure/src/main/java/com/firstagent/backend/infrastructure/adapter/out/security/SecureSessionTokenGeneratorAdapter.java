package com.firstagent.backend.infrastructure.adapter.out.security;

import com.firstagent.backend.application.port.out.SessionTokenGeneratorPort;
import com.firstagent.backend.domain.model.valueobject.OnboardingSessionToken;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class SecureSessionTokenGeneratorAdapter implements SessionTokenGeneratorPort {
    private final SecureRandom random = new SecureRandom();

    @Override
    public OnboardingSessionToken generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String tokenString = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new OnboardingSessionToken(tokenString);
    }
}
