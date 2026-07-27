package com.firstagent.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "Le numéro de compte est obligatoire")
    private String accountNumber;

    @NotBlank(message = "Le PIN est obligatoire")
    @Pattern(regexp = "^\\d{4,6}$", message = "Le PIN doit contenir entre 4 et 6 chiffres")
    private String pin;
}