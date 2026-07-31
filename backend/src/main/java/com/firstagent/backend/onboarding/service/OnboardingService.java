package com.firstagent.backend.onboarding.service;

import com.firstagent.backend.onboarding.dto.*;

public interface OnboardingService {

    void requestEmailOtp(String sessionToken, KycRequest request);

    void verifyEmailOtp(String sessionToken, OtpVerificationRequest request);

    // Contournement temporaire tant que l'envoi d'e-mail (SMTP) est en panne : permet de passer
    // l'étape KYC sans adresse e-mail ni OTP. À retirer une fois l'envoi d'e-mail rétabli.
    void skipEmailVerification(String sessionToken);

    void createProfile(String sessionToken, PinCreationRequest pinRequest);

    void acceptTerms(String sessionToken, TermsAcceptanceRequest request);

    OnboardingCompletionResponse completeOnboarding(String sessionToken);
}
