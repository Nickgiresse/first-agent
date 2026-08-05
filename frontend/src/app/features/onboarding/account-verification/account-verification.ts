import { Location } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AccountService } from '../../../core/services/account';
import { OnboardingState } from '../../../core/services/onboarding-state';
import { AccountNumberInput } from '../../../shared/components/account-number-input/account-number-input';

@Component({
  selector: 'app-account-verification',
  imports: [AccountNumberInput],
  templateUrl: './account-verification.html',
  styleUrl: './account-verification.scss'
})
export class AccountVerification {
  readonly lang = inject(LanguageService);

  private readonly accountService = inject(AccountService);
  private readonly state = inject(OnboardingState);
  private readonly router = inject(Router);
  private readonly location = inject(Location);

  readonly digits = signal('');
  readonly valid = computed(() => /^\d{18}$/.test(this.digits()));

  readonly touched = signal(false);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  goBack(): void {
    this.location.back();
  }

  onAccountDigitsChange(val: string): void {
    this.digits.set(val);
  }

  submit(): void {
    this.touched.set(true);
    if (!this.valid() || this.submitting()) return;

    this.submitting.set(true);
    this.error.set(null);
    this.accountService.verifyAccount(this.digits()).subscribe({
      next: response => {
        const data = response.data;
        if (!response.success || !data.eligible) {
          this.error.set(response.message || 'Ce compte ne peut pas être utilisé.');
          this.submitting.set(false);
          return;
        }
        this.state.setAccountVerified(data.sessionToken, data.firstName, data.lastName, data.expiresInSeconds);
        this.router.navigateByUrl('/onboarding/kyc');
      },
      error: err => {
        this.error.set(err?.message ?? err?.error?.message ?? 'Impossible de vérifier ce compte. Veuillez réessayer.');
        this.submitting.set(false);
      }
    });
  }
}

