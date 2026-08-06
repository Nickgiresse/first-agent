package com.firstagent.backend.onboarding.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TermsAcceptanceRequest {

  @AssertTrue(message = "Vous devez accepter les conditions d'utilisation pour continuer")
  private boolean termsAccepted;
}
