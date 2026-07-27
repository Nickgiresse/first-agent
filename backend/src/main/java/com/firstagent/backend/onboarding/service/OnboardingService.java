package com.firstagent.backend.onboarding.service;

import com.firstagent.backend.onboarding.dto.*;
import com.firstagent.backend.onboarding.entity.Customer;

public interface OnboardingService {

    void validateKyc(String sessionToken, KycRequest request);

    Customer createProfile(String sessionToken, KycRequest kycRequest, PinCreationRequest pinRequest);

    void acceptTerms(String sessionToken, TermsAcceptanceRequest request);

    OnboardingCompletionResponse completeOnboarding(String sessionToken);
}