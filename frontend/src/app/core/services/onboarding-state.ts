import { Injectable, signal } from '@angular/core';
import { OnboardingSessionState } from '../models/onboarding-session.model';

const STORAGE_KEY = 'onboarding-session-state';

const INITIAL_STATE: OnboardingSessionState = {
  sessionToken: null,
  firstName: null,
  lastName: null,
  email: null,
  expiresAt: null,
  linkToken: null,
  pin: null
};

/**
 * Champs jamais écrits dans le navigateur.
 *
 * Le code PIN est un secret bancaire. Persisté, il reste lisible dans
 * l'onglet bien après la finalisation, accessible à toute extension, à tout
 * script injecté et à quiconque ouvre les outils de développement sur un
 * poste partagé. La charte frontend l'interdit à trois endroits : §12.4 pour
 * les jetons, §13.2 pour les données sensibles d'un formulaire bancaire,
 * §13.4 pour les données personnelles d'un parcours long.
 *
 * ÉCART ASSUMÉ ET DOCUMENTÉ : `sessionToken` reste persisté. C'est aussi un
 * porteur d'autorisation, et §12.4 le vise également, mais le retirer ferait
 * perdre le parcours à chaque rechargement de page. La correction conforme
 * n'est pas de le supprimer : §13.4 demande une « sauvegarde de brouillon
 * côté serveur », donc une reprise portée par un cookie inaccessible au
 * script. C'est un chantier distinct, à mener avec le backend.
 */
const CHAMPS_NON_PERSISTES = ['pin'] as const;

function readFromStorage(): OnboardingSessionState {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? { ...INITIAL_STATE, ...JSON.parse(raw) } : INITIAL_STATE;
  } catch {
    return INITIAL_STATE;
  }
}

@Injectable({ providedIn: 'root' })
export class OnboardingState {
  private readonly state = signal<OnboardingSessionState>(readFromStorage());

  readonly sessionToken = (): string | null => this.state().sessionToken;
  readonly firstName = (): string | null => this.state().firstName;
  readonly lastName = (): string | null => this.state().lastName;
  readonly email = (): string | null => this.state().email;
  readonly linkToken = (): string | null => this.state().linkToken;
  readonly pin = (): string | null => this.state().pin;

  setAccountVerified(sessionToken: string, firstName: string, lastName: string, expiresInSeconds: number): void {
    const current = this.state();
    this.persist({
      sessionToken,
      firstName,
      lastName,
      email: null,
      expiresAt: Date.now() + expiresInSeconds * 1000,
      // On préserve le token du lien et le PIN déjà captés (l'entrée par lien précède la
      // vérification du compte) : ils sont nécessaires à la finalisation.
      linkToken: current.linkToken,
      pin: current.pin
    });
  }

  setEmail(email: string | null): void {
    this.persist({ ...this.state(), email });
  }

  // Token du lien d'onboarding (?t=), capté à l'entrée du parcours.
  setLinkToken(linkToken: string): void {
    this.persist({ ...this.state(), linkToken });
  }

  /**
   * Code PIN, conservé EN MÉMOIRE seulement jusqu'à la finalisation.
   *
   * Il est transmis en HTTPS et haché côté banque ; entre-temps il ne doit
   * exister que dans cet onglet, sans trace persistante. Un rechargement de
   * page le perd, ce qui est le comportement attendu : mieux vaut redemander
   * le code que le laisser traîner.
   */
  setPin(pin: string | null): void {
    this.persist({ ...this.state(), pin });
  }

  isSessionValid(): boolean {
    const current = this.state();
    return !!current.sessionToken && !!current.expiresAt && current.expiresAt > Date.now();
  }

  clear(): void {
    this.persist(INITIAL_STATE);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // stockage indisponible : rien à purger
    }
  }

  private persist(next: OnboardingSessionState): void {
    // L'état complet vit en mémoire ; seule la part non secrète est écrite.
    this.state.set(next);
    try {
      const persistable: Record<string, unknown> = { ...next };
      for (const champ of CHAMPS_NON_PERSISTES) {
        delete persistable[champ];
      }
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(persistable));
    } catch {
      // stockage indisponible (navigation privée...) : la session reste en mémoire pour cet onglet
    }
  }
}
