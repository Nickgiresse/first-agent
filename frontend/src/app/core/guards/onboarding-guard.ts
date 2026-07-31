import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { OnboardingState } from '../services/onboarding-state';

export const onboardingGuard: CanActivateFn = () => {
  const onboardingState = inject(OnboardingState);
  const router = inject(Router);

  if (onboardingState.isSessionValid()) {
    return true;
  }

  router.navigate(['/onboarding/account-verification']);
  return false;
};