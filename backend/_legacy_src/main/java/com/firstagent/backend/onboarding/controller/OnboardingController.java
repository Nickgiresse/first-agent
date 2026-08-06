package com.firstagent.backend.onboarding.controller;

import com.firstagent.backend.common.dto.ApiResponse;
import com.firstagent.backend.onboarding.dto.*;
import com.firstagent.backend.onboarding.service.OnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    // Entrée par lien : le microservice reçoit le JWT dans l'URL (?t=). Endpoint public (aucune
    // session n'existe encore) : il valide le lien auprès du WhatsApp banking et renvoie le contexte.
    @PostMapping("/link/verify")
    public ResponseEntity<ApiResponse<LinkVerificationResponse>> verifyLink(
        @Valid @RequestBody LinkVerifyRequest request
    ) {
        LinkVerificationResponse response = onboardingService.verifyOnboardingLink(request.getToken());
        return ResponseEntity.ok(ApiResponse.success(response, "Lien vérifié"));
    }

    @PostMapping("/kyc/otp/request")
    public ResponseEntity<ApiResponse<Void>> requestEmailOtp(
        @RequestHeader("X-Session-Token") String sessionToken,
        @Valid @RequestBody KycRequest request
    ) {
        onboardingService.requestEmailOtp(sessionToken, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Code de vérification envoyé par e-mail"));
    }

    @PostMapping("/kyc/otp/verify")
    public ResponseEntity<ApiResponse<Void>> verifyEmailOtp(
        @RequestHeader("X-Session-Token") String sessionToken,
        @Valid @RequestBody OtpVerificationRequest request
    ) {
        onboardingService.verifyEmailOtp(sessionToken, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Adresse e-mail vérifiée"));
    }

    @PostMapping("/kyc/skip")
    public ResponseEntity<ApiResponse<Void>> skipEmail(
        @RequestHeader("X-Session-Token") String sessionToken
    ) {
        onboardingService.skipEmailVerification(sessionToken);
        return ResponseEntity.ok(ApiResponse.success(null, "Étape de l'e-mail ignorée"));
    }

    @PostMapping("/profile")
    public ResponseEntity<ApiResponse<Void>> createProfile(
        @RequestHeader("X-Session-Token") String sessionToken,
        @Valid @RequestBody ProfileCreationRequest request
    ) {
        onboardingService.createProfile(sessionToken, request.getPin());
        return ResponseEntity.ok(ApiResponse.success(null, "Profil créé avec succès"));
    }

    @PostMapping("/terms")
    public ResponseEntity<ApiResponse<Void>> acceptTerms(
        @RequestHeader("X-Session-Token") String sessionToken,
        @Valid @RequestBody TermsAcceptanceRequest request
    ) {
        onboardingService.acceptTerms(sessionToken, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Conditions acceptées"));
    }

    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<OnboardingCompletionResponse>> completeOnboarding(
        @RequestHeader("X-Session-Token") String sessionToken,
        @RequestBody(required = false) CompleteOnboardingRequest request
    ) {
        OnboardingCompletionResponse response = onboardingService.completeOnboarding(sessionToken, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Onboarding terminé avec succès"));
    }
}