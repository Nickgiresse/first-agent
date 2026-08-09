import { Location } from '@angular/common';
import { errorMessage } from '../../../core/utils/error-message';
import { NavigationService } from '../../../core/services/navigation';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { AccountService } from '../../../core/services/account';
import { LanguageService } from '../../../core/services/language';
import { OnboardingState } from '../../../core/services/onboarding-state';
import { AccountNumberInput } from '../../../shared/components/account-number-input/account-number-input';

@Component({
  selector: 'afb-account-verification',
  changeDetection: ChangeDetectionStrategy.OnPush,
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
    // Effacer l'erreur dès la correction : « Compte introuvable » resterait
    // sinon affichée sous un champ que le client vient de modifier, et lui
    // laisserait croire que sa correction est refusée elle aussi.
    this.error.set(null);
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
        // Voir kyc.ts : `submitting` retombe avant la navigation, faute de quoi
        // une navigation refusée laisserait le client devant un bouton inerte.
        this.submitting.set(false);
        this.navigation.navigateTo('/onboarding/kyc');
      },
      error: err => {
        this.error.set(
          errorMessage(err, 'Impossible de vérifier ce compte. Veuillez réessayer.')
        );
        this.submitting.set(false);
      }
    });
  }
}

