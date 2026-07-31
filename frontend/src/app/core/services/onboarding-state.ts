import { Injectable, signal } from '@angular/core';
import { OnboardingSessionState } from '../models/onboarding-session.model';

const STORAGE_KEY = 'onboarding-session-state';

const INITIAL_STATE: OnboardingSessionState = {
  sessionToken: null,
  firstName: null,
  lastName: null,
  email: null,
  expiresAt: null
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

  setAccountVerified(sessionToken: string, firstName: string, lastName: string, expiresInSeconds: number): void {
    this.persist({
      sessionToken,
      firstName,
      lastName,
      email: null,
      expiresAt: Date.now() + expiresInSeconds * 1000
    });
  }

  setEmail(email: string | null): void {
    this.persist({ ...this.state(), email });
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
