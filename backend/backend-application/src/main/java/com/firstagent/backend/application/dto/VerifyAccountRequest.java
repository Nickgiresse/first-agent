package com.firstagent.backend.application.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyAccountRequest(
    @NotBlank(message = "Le suffixe de compte est obligatoire")
    String accountSuffix
) {}
