import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { OnboardingState } from '../services/onboarding-state';

/**
 * Interdit l'accès aux étapes du parcours sans session valide.
 *
 * Le garde rend un {@link UrlTree} plutôt que de déclencher lui-même une
 * navigation avant de rendre `false`. Le résultat visible est le même, mais
 * la seconde forme lance deux navigations concurrentes : celle que le garde
 * déclenche, et l'annulation de celle en cours. C'est au routeur d'enchaîner,
 * et lui seul sait le faire dans le bon ordre.
 *
 * Accessoirement, cette forme n'abandonne aucune promesse derrière elle.
 */
export const onboardingGuard: CanActivateFn = (): boolean | UrlTree => {
  const onboardingState = inject(OnboardingState);
  const router = inject(Router);

  if (onboardingState.isSessionValid()) {
    return true;
  }

  return router.createUrlTree(['/onboarding/account-verification']);
};
