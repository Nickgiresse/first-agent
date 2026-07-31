export interface OnboardingSessionState {
  sessionToken: string | null;
  firstName: string | null;
  lastName: string | null;
  email: string | null;
  expiresAt: number | null;
}
