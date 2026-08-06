package com.firstagent.backend.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record KycRequest(
    @NotBlank(message = "L'adresse e-mail est obligatoire")
    @Email(message = "L'adresse e-mail doit être valide")
    String email
) {}
