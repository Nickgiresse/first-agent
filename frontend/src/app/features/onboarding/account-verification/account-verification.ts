import { Location } from '@angular/common';
import { NavigationService } from '../../../core/services/navigation';
import { Component, computed, inject, signal } from '@angular/core';
import { AccountService } from '../../../core/services/account';
import { LanguageService } from '../../../core/services/language';
import { OnboardingState } from '../../../core/services/onboarding-state';
import { AccountNumberInput } from '../../../shared/components/account-number-input/account-number-input';

@Component({
  selector: 'afb-account-verification',
  imports: [AccountNumberInput],
  templateUrl: './account-verification.html',
  styleUrl: './account-verification.scss'
})
export class AccountVerification {
  private readonly navigation = inject(NavigationService);
  readonly lang = inject(LanguageService);

  private readonly accountService = inject(AccountService);
  private readonly state = inject(OnboardingState);
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
        this.navigation.navigateTo('/onboarding/kyc');
      },
      error: err => {
        this.error.set(err?.message ?? err?.error?.message ?? 'Impossible de vérifier ce compte. Veuillez réessayer.');
        this.submitting.set(false);
      }
    });
  }
}

