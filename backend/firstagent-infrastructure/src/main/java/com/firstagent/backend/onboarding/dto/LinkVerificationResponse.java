package com.firstagent.backend.onboarding.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Contexte résolu à partir du lien d'onboarding (via l'API M2M du WhatsApp banking) : sert au
 * frontend à démarrer le parcours (pré-remplissage, langue) sans que le client resaisisse ce qui
 * est déjà connu. Le jeton lui-même n'est pas renvoyé : le frontend conserve celui de l'URL et le
 * re-transmet à la finalisation.
 */
@Getter
@Builder
public class LinkVerificationResponse {

    private final boolean valid;
    private final String phone;
    private final String name;
    private final String accountNumber;
    private final String lang;
    private final boolean alreadyOnboarded;
}
