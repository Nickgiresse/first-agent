package com.firstagent.backend.onboarding.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Données transmises à la finalisation pour pousser le client vers le WhatsApp banking
 * (source de vérité) :
 *   - linkToken : le JWT du lien d'onboarding (?t=), validé et CONSOMMÉ côté WhatsApp banking ;
 *   - pin       : le code PIN en clair (déjà saisi à l'étape profil, conservé côté client),
 *                 transmis en HTTPS pour être haché dans le schéma de la banque. Jamais persisté
 *                 en clair côté backend.
 *
 * Champs facultatifs : si linkToken/pin sont absents (ex. parcours interne de test non initié par
 * un lien), la finalisation reste locale et le push est simplement ignoré.
 */
@Getter
@Setter
public class CompleteOnboardingRequest {

    private String linkToken;

    private String pin;
}
