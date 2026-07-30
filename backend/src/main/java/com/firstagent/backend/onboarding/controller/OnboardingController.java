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

    @PostMapping("/kyc")
    public ResponseEntity<ApiResponse<Void>> validateKyc(
        @RequestHeader("X-Session-Token") String sessionToken,
        @Valid @RequestBody KycRequest request
    ) {
        onboardingService.validateKyc(sessionToken, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Informations KYC validées"));
    }

    @PostMapping("/profile")
    public ResponseEntity<ApiResponse<Void>> createProfile(
        @RequestHeader("X-Session-Token") String sessionToken,
        @Valid @RequestBody ProfileCreationRequest request
    ) {
        onboardingService.createProfile(sessionToken, request.getKyc(), request.getPin());
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