package com.firstagent.backend.onboarding.controller;

import com.firstagent.backend.common.dto.ApiResponse;
import com.firstagent.backend.onboarding.dto.CompleteOnboardingRequest;
import com.firstagent.backend.onboarding.dto.KycRequest;
import com.firstagent.backend.onboarding.dto.LinkVerificationResponse;
import com.firstagent.backend.onboarding.dto.LinkVerifyRequest;
import com.firstagent.backend.onboarding.dto.OnboardingCompletionResponse;
import com.firstagent.backend.onboarding.dto.OtpVerificationRequest;
import com.firstagent.backend.onboarding.dto.ProfileCreationRequest;
import com.firstagent.backend.onboarding.dto.TermsAcceptanceRequest;
import com.firstagent.backend.onboarding.service.OnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

  private final OnboardingService onboardingService;

  // Entrée par lien : le microservice reçoit le JWT dans l'URL (?t=). Endpoint public (aucune
  // session n'existe encore) : il valide le lien auprès du WhatsApp banking et renvoie le contexte.
  @PostMapping("/link/verify")
  public ResponseEntity<ApiResponse<LinkVerificationResponse>> verifyLink(
      @Valid @RequestBody LinkVerifyRequest request) {
    LinkVerificationResponse response = onboardingService.verifyOnboardingLink(request.getToken());
    return ResponseEntity.ok(ApiResponse.success(response, "Lien vérifié"));
  }

  @PostMapping("/kyc/otp/request")
  public ResponseEntity<ApiResponse<Void>> requestEmailOtp(
      @RequestHeader("X-Session-Token") String sessionToken,
      @Valid @RequestBody KycRequest request) {
    onboardingService.requestEmailOtp(sessionToken, request);
    return ResponseEntity.ok(ApiResponse.success(null, "Code de vérification envoyé par e-mail"));
  }

  @PostMapping("/kyc/otp/verify")
  public ResponseEntity<ApiResponse<Void>> verifyEmailOtp(
      @RequestHeader("X-Session-Token") String sessionToken,
      @Valid @RequestBody OtpVerificationRequest request) {
    onboardingService.verifyEmailOtp(sessionToken, request);
    return ResponseEntity.ok(ApiResponse.success(null, "Adresse e-mail vérifiée"));
  }

  // Le contournement /kyc/skip a été retiré. Il avait été ajouté « tant que
  // l'envoi d'e-mail est en panne », mais l'envoi fonctionne : serveur, port
  // et compte configurés sont corrects, et l'authentification Office 365
  // passe depuis le poste. Seule la variable MAIL_PASSWORD n'était pas
  // renseignée, ce qui faisait échouer le démarrage sur un placeholder non
  // résolu et a été pris pour une panne — voir .env.example.
  //
  // Le conserver aurait été plus qu'une étape sautée : le bouton était offert
  // au client dans l'interface, et rien ne distinguait un envoi réellement en
  // échec d'un client préférant ne pas donner son adresse. Or le courriel
  // porte les codes à usage unique et le lien de réinitialisation du PIN : un
  // compte activé sans adresse vérifiée n'offre aucun moyen de récupération.

  @PostMapping("/profile")
  public ResponseEntity<ApiResponse<Void>> createProfile(
      @RequestHeader("X-Session-Token") String sessionToken,
      @Valid @RequestBody ProfileCreationRequest request) {
    onboardingService.createProfile(sessionToken, request.getPin());
    return ResponseEntity.ok(ApiResponse.success(null, "Profil créé avec succès"));
  }

  @PostMapping("/terms")
  public ResponseEntity<ApiResponse<Void>> acceptTerms(
      @RequestHeader("X-Session-Token") String sessionToken,
      @Valid @RequestBody TermsAcceptanceRequest request) {
    onboardingService.acceptTerms(sessionToken, request);
    return ResponseEntity.ok(ApiResponse.success(null, "Conditions acceptées"));
  }

  @PostMapping("/complete")
  public ResponseEntity<ApiResponse<OnboardingCompletionResponse>> completeOnboarding(
      @RequestHeader("X-Session-Token") String sessionToken,
      @RequestBody(required = false) CompleteOnboardingRequest request) {
    OnboardingCompletionResponse response =
        onboardingService.completeOnboarding(sessionToken, request);
    return ResponseEntity.ok(ApiResponse.success(response, "Onboarding terminé avec succès"));
  }
}
