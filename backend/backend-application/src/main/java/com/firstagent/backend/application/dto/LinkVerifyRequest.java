package com.firstagent.backend.application.dto;

import jakarta.validation.constraints.NotBlank;

public record LinkVerifyRequest(
    @NotBlank(message = "Le jeton est obligatoire")
    String token
) {}
