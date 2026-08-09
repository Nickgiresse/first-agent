import { Location } from '@angular/common';
import { errorMessage } from '../../../core/utils/error-message';
import { NavigationService } from '../../../core/services/navigation';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { OnboardingService } from '../../../core/services/onboarding';
import { OnboardingState } from '../../../core/services/onboarding-state';
import { LanguageService } from '../../../core/services/language';

type Phase = 'EMAIL' | 'OTP';

const RESEND_COOLDOWN_SECONDS = 45;

@Component({ selector: 'afb-kyc',
  changeDetection: ChangeDetectionStrategy.OnPush, imports: [ReactiveFormsModule], templateUrl: './kyc.html', styleUrl: './kyc.scss' })
export class Kyc {
  private readonly navigation = inject(NavigationService);
  readonly lang = inject(LanguageService);

  private readonly state = inject(OnboardingState);
  private readonly service = inject(OnboardingService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly location = inject(Location);

  readonly firstName = this.state.firstName;
  readonly lastName = this.state.lastName;

  readonly phase = signal<Phase>('EMAIL');
  readonly email = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] });
  readonly code = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^\d{6}$/)] });

  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly cooldownRemaining = signal(0);

  goBack(): void {
    if (this.phase() === 'OTP') {
      this.changeEmail();
    } else {
      this.location.back();
    }
  }

  private cooldownTimer: ReturnType<typeof setInterval> | undefined;

  constructor() {
    this.destroyRef.onDestroy(() => this.stopCooldown());
  }

  digitsOnly(): void {
    this.code.setValue(this.code.value.replace(/\D/g, '').slice(0, 6));
  }

  requestCode(): void {
    if (this.email.invalid || this.submitting()) {
      this.email.markAsTouched();
      return;
    }
    // Le délai entre deux envois se contrôle ICI, seul point d'envoi, et non
    // dans resendCode(). Placé là-bas, il se contournait en deux clics :
    // « Modifier l'adresse » ramène au formulaire en arrêtant le décompte, et
    // l'envoi repartait sans aucune garde, autant de fois que voulu. Chaque
    // envoi coûte un courriel et ouvre une fenêtre de code valide.
    if (this.cooldownRemaining() > 0) {
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.service.requestEmailOtp({ email: this.email.value }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.phase.set('OTP');
        this.code.reset('');
        this.startCooldown();
      },
      error: err => {
        this.error.set(errorMessage(err, 'Impossible d’envoyer le code de vérification.'));
        this.submitting.set(false);
      }
    });
  }

  resendCode(): void {
    if (this.submitting()) return;
    this.requestCode();
  }

  // skipEmail retiré : l'envoi d'e-mail fonctionne, seule la variable
  // MAIL_PASSWORD n'était pas renseignée côté backend.

  changeEmail(): void {
    this.phase.set('EMAIL');
    this.error.set(null);
    // Le décompte n'est PAS arrêté : il protège l'envoi, pas l'écran. L'arrêter
    // ici rendait la protection inopérante, et laissait de surcroît un compteur
    // figé à sa dernière valeur si la nouvelle demande échouait.
  }

  verifyCode(): void {
    if (this.code.invalid || this.submitting()) {
      this.code.markAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.service.verifyEmailOtp({ code: this.code.value }).subscribe({
      next: () => {
        this.state.setEmail(this.email.value);
        // `submitting` retombe AVANT de naviguer. Le laisser à vrai ne se
        // remarque pas tant que la navigation aboutit, mais un garde qui
        // refuse laisserait le client devant un bouton « Vérification… »
        // définitivement inerte, sans message.
        this.submitting.set(false);
        this.navigation.navigateTo('/onboarding/pin-creation');
      },
      error: err => {
        this.error.set(errorMessage(err, 'Code de vérification incorrect.'));
        this.submitting.set(false);
      }
    });
  }

  private startCooldown(): void {
    this.stopCooldown();
    this.cooldownRemaining.set(RESEND_COOLDOWN_SECONDS);
    this.cooldownTimer = setInterval(() => {
      const next = this.cooldownRemaining() - 1;
      if (next <= 0) {
        this.cooldownRemaining.set(0);
        this.stopCooldown();
      } else {
        this.cooldownRemaining.set(next);
      }
    }, 1000);
  }

  private stopCooldown(): void {
    if (this.cooldownTimer) {
      clearInterval(this.cooldownTimer);
      this.cooldownTimer = undefined;
    }
  }
}
