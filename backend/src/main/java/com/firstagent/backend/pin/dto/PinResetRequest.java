package com.firstagent.backend.pin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PinResetRequest {

    @NotBlank(message = "Le numéro de compte est obligatoire")
    private String accountNumber;
}