package com.firstagent.backend.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OtpVerificationRequest {
  @NotBlank(message = "Le code de vérification est obligatoire")
  @Pattern(regexp = "\\d{6}", message = "Le code doit contenir exactement 6 chiffres")
  private String code;
}
