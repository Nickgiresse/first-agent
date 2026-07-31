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

    // Contournement temporaire tant que l'envoi d'e-mail (SMTP) est en panne — voir OnboardingService.
    @PostMapping("/kyc/skip")
    public ResponseEntity<ApiResponse<Void>> skipEmailVerification(
        @RequestHeader("X-Session-Token") String sessionToken
    ) {
        onboardingService.skipEmailVerification(sessionToken);
        return ResponseEntity.ok(ApiResponse.success(null, "Étape KYC passée sans e-mail"));
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
        @RequestHeader("X-Session-Token") String sessionToken
    ) {
        OnboardingCompletionResponse response = onboardingService.completeOnboarding(sessionToken);
        return ResponseEntity.ok(ApiResponse.success(response, "Onboarding terminé avec succès"));
    }
}