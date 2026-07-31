import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'onboarding/welcome',
    pathMatch: 'full'
  },
  {
    path: 'onboarding',
    loadChildren: () =>
      import('./features/onboarding/onboarding.routes').then(m => m.ONBOARDING_ROUTES)
  },
  {
    path: 'forgot-pin',
    loadComponent: () =>
      import('./features/auth/forgot-pin/forgot-pin').then(m => m.ForgotPin)
  },
  {
    path: '**',
    redirectTo: 'onboarding/welcome'
  }
];