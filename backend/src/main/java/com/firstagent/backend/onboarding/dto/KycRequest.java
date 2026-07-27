package com.firstagent.backend.onboarding.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KycRequest {

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(max = 100, message = "Le prénom ne doit pas dépasser 100 caractères")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
    private String lastName;

    @Email(message = "Format d'email invalide")
    @Size(max = 150, message = "L'email ne doit pas dépasser 150 caractères")
    private String email;
}
