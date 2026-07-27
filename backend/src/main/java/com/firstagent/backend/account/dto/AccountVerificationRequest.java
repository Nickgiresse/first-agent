package com.firstagent.backend.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountVerificationRequest {

    @NotBlank(message = "Le numéro de compte est obligatoire")
    @Size(min = 5, max = 34, message = "Le numéro de compte doit contenir entre 5 et 34 caractères")
    private String accountNumber;
}