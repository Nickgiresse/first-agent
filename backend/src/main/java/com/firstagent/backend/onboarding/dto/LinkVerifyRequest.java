package com.firstagent.backend.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Corps de la requête d'entrée par lien : le JWT reçu dans l'URL du microservice (?t=). */
@Getter
@Setter
public class LinkVerifyRequest {

    @NotBlank(message = "Le jeton du lien est obligatoire")
    private String token;
}
