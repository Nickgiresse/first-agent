package com.firstagent.backend.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountVerificationRequest {
    @NotBlank(message = "Les 18 chiffres du compte sont obligatoires")
    @Pattern(regexp = "\\d{18}", message = "Le suffixe doit contenir exactement 18 chiffres")
    private String accountSuffix;
}
