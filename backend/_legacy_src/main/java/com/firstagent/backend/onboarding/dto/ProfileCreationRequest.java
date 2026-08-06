package com.firstagent.backend.onboarding.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileCreationRequest {

    @NotNull(message = "Les informations de PIN sont obligatoires")
    @Valid
    private PinCreationRequest pin;
}