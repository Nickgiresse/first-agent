export interface OnboardingSessionState {
  sessionToken: string | null;
  firstName: string | null;
  lastName: string | null;
  email: string | null;
  expiresAt: number | null;
  // JWT du lien d'onboarding (?t=) : capté à l'entrée, retransmis au WhatsApp banking à la
  // finalisation (source de vérité), qui le consomme (usage unique).
  linkToken: string | null;
  // PIN en clair, conservé le temps du parcours pour être transmis à la finalisation en HTTPS
  // (haché dans le schéma de la banque). Effacé au clear() une fois l'onboarding terminé.
  pin: string | null;
}
