package com.firstagent.backend.application.usecase;

import com.firstagent.backend.application.dto.*;
import com.firstagent.backend.application.port.in.OnboardingUseCase;
import com.firstagent.backend.application.port.out.OnboardingSessionRepositoryPort;
import com.firstagent.backend.domain.exception.BusinessRuleException;
import com.firstagent.backend.domain.model.valueobject.OnboardingSessionToken;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class OnboardingUseCaseImpl implements OnboardingUseCase {

    private static final Logger log = LoggerFactory.getLogger(OnboardingUseCaseImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OnboardingSessionRepositoryPort onboardingSessionRepositoryPort;
    private final Map<String, String> otpCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> attemptsCache = new ConcurrentHashMap<>();

    public OnboardingUseCaseImpl(OnboardingSessionRepositoryPort onboardingSessionRepositoryPort) {
        this.onboardingSessionRepositoryPort = Objects.requireNonNull(onboardingSessionRepositoryPort);
    }

    @Override
    public Mono<LinkVerificationResponse> verifyLink(String token) {
        return Mono.just(new LinkVerificationResponse(true, "+237600000000", "CLIENT DEMO", "10005001", "fr", false));
    }

    @Override
    public Mono<Void> requestEmailOtp(String sessionToken, KycRequest request) {
        if (request == null || request.email() == null || request.email().isBlank()) {
            return Mono.error(new BusinessRuleException("RG-OTP-003", "Veuillez indiquer une adresse e-mail valide"));
        }

        String key = (sessionToken != null && !sessionToken.isBlank()) ? sessionToken : request.email().toLowerCase().trim();

        // Code OTP à 6 chiffres
        String generatedOtp = String.format("%06d", RANDOM.nextInt(1000000));
        otpCache.put(key, generatedOtp);
        attemptsCache.put(key, 0);

        log.info("Code OTP généré pour [{}] : {}", key, generatedOtp);

        if (sessionToken != null && !sessionToken.isBlank()) {
            return onboardingSessionRepositoryPort.findBySessionToken(new OnboardingSessionToken(sessionToken))
                    .then();
        }
        return Mono.empty();
    }

    @Override
    public Mono<Void> verifyEmailOtp(String sessionToken, OtpVerificationRequest request) {
        String submittedCode = request != null ? request.getEffectiveCode() : null;
        if (submittedCode == null || !submittedCode.matches("^\\d{6}$")) {
            return Mono.error(new BusinessRuleException("RG-OTP-001", "Le code de vérification doit comporter exactement 6 chiffres"));
        }

        String key = (sessionToken != null && !sessionToken.isBlank()) ? sessionToken : "DEFAULT";
        String expectedOtp = otpCache.get(key);

        int attempts = attemptsCache.getOrDefault(key, 0);
        if (attempts >= 5) {
            return Mono.error(new BusinessRuleException("RG-OTP-004", "Nombre maximal de tentatives atteint (5/5). Veuillez solliciter un nouveau code"));
        }

        // Si un code est généré pour cette clé, il doit correspondre. Le code "123456" est également accepté pour la démonstration.
        boolean matches = (expectedOtp != null && expectedOtp.equals(submittedCode))
                       || "123456".equals(submittedCode);

        if (!matches) {
            attemptsCache.put(key, attempts + 1);
            int remaining = 5 - (attempts + 1);
            log.warn("Code OTP incorrect saisi pour [{}] (Tentative {}/5)", key, attempts + 1);
            return Mono.error(new BusinessRuleException("RG-OTP-002", "Code de vérification à 6 chiffres incorrect (" + remaining + " tentative(s) restante(s))"));
        }

        otpCache.remove(key);
        attemptsCache.remove(key);
        log.info("Code OTP vérifié avec succès pour [{}]", key);

        if (sessionToken != null && !sessionToken.isBlank()) {
            return onboardingSessionRepositoryPort.findBySessionToken(new OnboardingSessionToken(sessionToken))
                    .then();
        }
        return Mono.empty();
    }

    @Override
    public Mono<Void> skipEmail(String sessionToken) {
        if (sessionToken != null && !sessionToken.isBlank()) {
            return onboardingSessionRepositoryPort.findBySessionToken(new OnboardingSessionToken(sessionToken))
                    .then();
        }
        return Mono.empty();
    }

    @Override
    public Mono<Void> createProfile(String sessionToken, ProfileCreationRequest request) {
        if (sessionToken != null && !sessionToken.isBlank()) {
            return onboardingSessionRepositoryPort.findBySessionToken(new OnboardingSessionToken(sessionToken))
                    .then();
        }
        return Mono.empty();
    }

    @Override
    public Mono<Void> acceptTerms(String sessionToken, TermsAcceptanceRequest request) {
        if (sessionToken != null && !sessionToken.isBlank()) {
            return onboardingSessionRepositoryPort.findBySessionToken(new OnboardingSessionToken(sessionToken))
                    .then();
        }
        return Mono.empty();
    }

    @Override
    public Mono<OnboardingCompletionResponse> completeOnboarding(String sessionToken, CompleteOnboardingRequest request) {
        if (sessionToken != null && !sessionToken.isBlank()) {
            return onboardingSessionRepositoryPort.findBySessionToken(new OnboardingSessionToken(sessionToken))
                    .map(session -> new OnboardingCompletionResponse("CUST-" + session.getId().value().toString().substring(0, 8), "COMPLETED", "Onboarding effectué avec succès"))
                    .defaultIfEmpty(new OnboardingCompletionResponse("CUST-DEMO0001", "COMPLETED", "Onboarding effectué avec succès"));
        }
        return Mono.just(new OnboardingCompletionResponse("CUST-DEMO0001", "COMPLETED", "Onboarding effectué avec succès"));
    }
}
