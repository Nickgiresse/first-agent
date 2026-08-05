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

  readonly sessionToken = () => this.state().sessionToken;
  readonly firstName = () => this.state().firstName;
  readonly lastName = () => this.state().lastName;
  readonly email = () => this.state().email;
  readonly linkToken = () => this.state().linkToken;
  readonly pin = () => this.state().pin;

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

  // PIN en clair, conservé pour la finalisation (transmis en HTTPS, haché côté banque).
  setPin(pin: string | null): void {
    this.persist({ ...this.state(), pin });
  }

  isSessionValid(): boolean {
    const current = this.state();
    return !!current.sessionToken && !!current.expiresAt && current.expiresAt > Date.now();
  }

  clear(): void {
    this.persist(INITIAL_STATE);
  }

  private persist(next: OnboardingSessionState): void {
    this.state.set(next);
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    } catch {
      // stockage indisponible (navigation privée...) : la session reste en mémoire pour cet onglet
    }
  }
}
