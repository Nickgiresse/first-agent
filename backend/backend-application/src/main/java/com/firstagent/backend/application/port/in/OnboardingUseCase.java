package com.firstagent.backend.application.port.in;

import com.firstagent.backend.application.dto.*;
import reactor.core.publisher.Mono;

public interface OnboardingUseCase {
    Mono<LinkVerificationResponse> verifyLink(String token);
    Mono<Void> requestEmailOtp(String sessionToken, KycRequest request);
    Mono<Void> verifyEmailOtp(String sessionToken, OtpVerificationRequest request);
    Mono<Void> skipEmail(String sessionToken);
    Mono<Void> createProfile(String sessionToken, ProfileCreationRequest request);
    Mono<Void> acceptTerms(String sessionToken, TermsAcceptanceRequest request);
    Mono<OnboardingCompletionResponse> completeOnboarding(String sessionToken, CompleteOnboardingRequest request);
}
