package com.firstagent.backend.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PinCreationRequest {

  @NotBlank(message = "Le PIN est obligatoire")
  @Pattern(regexp = "^\\d{4,6}$", message = "Le PIN doit contenir entre 4 et 6 chiffres")
  private String pin;

  @NotBlank(message = "La confirmation du PIN est obligatoire")
  @Pattern(
      regexp = "^\\d{4,6}$",
      message = "Le PIN de confirmation doit contenir entre 4 et 6 chiffres")
  private String pinConfirmation;
}
