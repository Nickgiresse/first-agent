package com.firstagent.backend.pin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PinResetConfirmRequest {

  @NotBlank(message = "Le token de réinitialisation est obligatoire")
  private String resetToken;

  @NotBlank(message = "Le nouveau PIN est obligatoire")
  @Pattern(regexp = "^\\d{4,6}$", message = "Le PIN doit contenir entre 4 et 6 chiffres")
  private String newPin;

  @NotBlank(message = "La confirmation du nouveau PIN est obligatoire")
  @Pattern(regexp = "^\\d{4,6}$", message = "La confirmation doit contenir entre 4 et 6 chiffres")
  private String newPinConfirmation;
}
