package com.firstagent.backend.onboarding.service;

import com.firstagent.backend.onboarding.dto.CompleteOnboardingRequest;
import com.firstagent.backend.onboarding.dto.KycRequest;
import com.firstagent.backend.onboarding.dto.LinkVerificationResponse;
import com.firstagent.backend.onboarding.dto.OnboardingCompletionResponse;
import com.firstagent.backend.onboarding.dto.OtpVerificationRequest;
import com.firstagent.backend.onboarding.dto.PinCreationRequest;
import com.firstagent.backend.onboarding.dto.TermsAcceptanceRequest;

public interface OnboardingService {

  void requestEmailOtp(String sessionToken, KycRequest request);

  void verifyEmailOtp(String sessionToken, OtpVerificationRequest request);

  // skipEmailVerification retiré : l'envoi d'e-mail fonctionne, seule la
  // variable MAIL_PASSWORD manquait (voir .env.example et OnboardingController).

  void createProfile(String sessionToken, PinCreationRequest pinRequest);

  void acceptTerms(String sessionToken, TermsAcceptanceRequest request);

  // Entrée par lien : valide le JWT du lien d'onboarding (?t=) auprès du WhatsApp banking et
  // renvoie le contexte (téléphone, langue, identité connue) pour démarrer le parcours.
  LinkVerificationResponse verifyOnboardingLink(String token);

  OnboardingCompletionResponse completeOnboarding(
      String sessionToken, CompleteOnboardingRequest request);
}
