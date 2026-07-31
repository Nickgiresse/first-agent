import { Routes } from '@angular/router';
import { onboardingGuard } from '../../core/guards/onboarding-guard';

export const ONBOARDING_ROUTES: Routes = [
  {
    path: 'welcome',
    loadComponent: () =>
      import('./welcome/welcome').then(m => m.Welcome)
  },
  {
    path: 'account-verification',
    loadComponent: () =>
      import('./account-verification/account-verification').then(
        m => m.AccountVerification
      )
  },
  {
    path: 'kyc',
    canActivate: [onboardingGuard],
    loadComponent: () =>
      import('./kyc/kyc').then(m => m.Kyc)
  },
  {
    path: 'pin-creation',
    canActivate: [onboardingGuard],
    loadComponent: () =>
      import('./pin-creation/pin-creation').then(m => m.PinCreation)
  },
  {
    path: 'document-scan',
    canActivate: [onboardingGuard],
    loadComponent: () =>
      import('./document-scan/document-scan').then(m => m.DocumentScan)
  },
  {
    path: 'document-ocr-review',
    canActivate: [onboardingGuard],
    loadComponent: () =>
      import('./document-ocr-review/document-ocr-review').then(
        m => m.DocumentOcrReview
      )
  },
  {
    path: 'document-upload',
    canActivate: [onboardingGuard],
    loadComponent: () =>
      import('./document-upload/document-upload').then(
        m => m.DocumentUpload
      )
  },
  {
    path: 'liveness-challenge',
    canActivate: [onboardingGuard],
    loadComponent: () =>
      import('./liveness-challenge/liveness-challenge').then(
        m => m.LivenessChallenge
      )
  },
  {
    path: 'terms-conditions',
    canActivate: [onboardingGuard],
    loadComponent: () =>
      import('./terms-conditions/terms-conditions').then(
        m => m.TermsConditions
      )
  },
  {
    path: 'success',
    loadComponent: () =>
      import('./success/success').then(m => m.Success)
  },
  {
    path: '',
    redirectTo: 'welcome',
    pathMatch: 'full'
  }
];
