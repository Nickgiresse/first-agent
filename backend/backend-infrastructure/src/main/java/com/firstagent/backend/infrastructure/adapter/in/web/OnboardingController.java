package com.firstagent.backend.infrastructure.adapter.in.web;

import com.firstagent.backend.application.dto.*;
import com.firstagent.backend.application.port.in.OnboardingUseCase;
import com.firstagent.backend.common.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

    private final OnboardingUseCase onboardingUseCase;

    public OnboardingController(OnboardingUseCase onboardingUseCase) {
        this.onboardingUseCase = Objects.requireNonNull(onboardingUseCase);
    }

    @PostMapping("/link/verify")
    public Mono<ResponseEntity<ApiResponse<LinkVerificationResponse>>> verifyLink(
            @Valid @RequestBody LinkVerifyRequest request
    ) {
        return onboardingUseCase.verifyLink(request.token())
                .map(response -> ResponseEntity.ok(ApiResponse.success(response, "Lien vérifié")));
    }

    @PostMapping("/kyc/otp/request")
    public Mono<ResponseEntity<ApiResponse<Void>>> requestEmailOtp(
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
            @Valid @RequestBody KycRequest request
    ) {
        return onboardingUseCase.requestEmailOtp(sessionToken, request)
                .then(Mono.just(ResponseEntity.ok(ApiResponse.success(null, "Code de vérification envoyé par e-mail"))));
    }

    @PostMapping("/kyc/otp/verify")
    public Mono<ResponseEntity<ApiResponse<Void>>> verifyEmailOtp(
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
            @Valid @RequestBody OtpVerificationRequest request
    ) {
        return onboardingUseCase.verifyEmailOtp(sessionToken, request)
                .then(Mono.just(ResponseEntity.ok(ApiResponse.success(null, "Adresse e-mail vérifiée"))));
    }

    @PostMapping("/kyc/skip")
    public Mono<ResponseEntity<ApiResponse<Void>>> skipEmail(
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken
    ) {
        return onboardingUseCase.skipEmail(sessionToken)
                .then(Mono.just(ResponseEntity.ok(ApiResponse.success(null, "Étape de l'e-mail ignorée"))));
    }

    @PostMapping("/profile")
    public Mono<ResponseEntity<ApiResponse<Void>>> createProfile(
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
            @Valid @RequestBody ProfileCreationRequest request
    ) {
        return onboardingUseCase.createProfile(sessionToken, request)
                .then(Mono.just(ResponseEntity.ok(ApiResponse.success(null, "Profil créé avec succès"))));
    }

    @PostMapping("/terms")
    public Mono<ResponseEntity<ApiResponse<Void>>> acceptTerms(
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
            @Valid @RequestBody TermsAcceptanceRequest request
    ) {
        return onboardingUseCase.acceptTerms(sessionToken, request)
                .then(Mono.just(ResponseEntity.ok(ApiResponse.success(null, "Conditions acceptées"))));
    }

    @PostMapping("/complete")
    public Mono<ResponseEntity<ApiResponse<OnboardingCompletionResponse>>> completeOnboarding(
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
            @RequestBody(required = false) CompleteOnboardingRequest request
    ) {
        return onboardingUseCase.completeOnboarding(sessionToken, request)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response, "Onboarding terminé avec succès")));
    }
}
