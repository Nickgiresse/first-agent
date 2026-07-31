import { Location } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { OnboardingService } from '../../../core/services/onboarding';
import { OnboardingState } from '../../../core/services/onboarding-state';

type Phase = 'EMAIL' | 'OTP';

const RESEND_COOLDOWN_SECONDS = 45;

@Component({ selector: 'app-kyc', imports: [ReactiveFormsModule], templateUrl: './kyc.html', styleUrl: './kyc.scss' })
export class Kyc {
  private readonly state = inject(OnboardingState);
  private readonly service = inject(OnboardingService);
  private readonly router = inject(Router);
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
        this.error.set(err?.message ?? err?.error?.message ?? 'Impossible d’envoyer le code de vérification.');
        this.submitting.set(false);
      }
    });
  }

  resendCode(): void {
    if (this.cooldownRemaining() > 0 || this.submitting()) return;
    this.requestCode();
  }

  // Contournement temporaire tant que l'envoi d'e-mail (SMTP) est en panne.
  skipEmail(): void {
    if (this.submitting()) return;
    this.submitting.set(true);
    this.error.set(null);
    this.service.skipEmailVerification().subscribe({
      next: () => this.router.navigateByUrl('/onboarding/pin-creation'),
      error: err => {
        this.error.set(err?.message ?? err?.error?.message ?? 'Impossible de continuer sans e-mail.');
        this.submitting.set(false);
      }
    });
  }

  changeEmail(): void {
    this.phase.set('EMAIL');
    this.error.set(null);
    this.stopCooldown();
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
        this.router.navigateByUrl('/onboarding/pin-creation');
      },
      error: err => {
        this.error.set(err?.message ?? err?.error?.message ?? 'Code de vérification incorrect.');
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
