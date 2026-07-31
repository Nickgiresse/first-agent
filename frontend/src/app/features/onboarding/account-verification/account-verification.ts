import { Location } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AccountService } from '../../../core/services/account';
import { OnboardingState } from '../../../core/services/onboarding-state';

// Groupement d'affichage du numéro de compte (5-11-2 = 18 chiffres), aligné sur le placeholder
// historique "_____ ___________ __". Purement visuel : la valeur envoyée au backend (voir submit())
// reste les 18 chiffres bruts, sans espace ni underscore.
const GROUP_SIZES = [5, 11, 2];
const TOTAL_DIGITS = 18;

@Component({
  selector: 'app-account-verification',
  imports: [],
  templateUrl: './account-verification.html',
  styleUrl: './account-verification.scss'
})
export class AccountVerification {
  private readonly accountService = inject(AccountService);
  private readonly state = inject(OnboardingState);
  private readonly router = inject(Router);
  private readonly location = inject(Location);

  readonly digits = signal('');
  readonly displayValue = computed(() => formatWithPlaceholders(this.digits()));
  readonly valid = computed(() => /^\d{18}$/.test(this.digits()));

  readonly touched = signal(false);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  goBack(): void {
    this.location.back();
  }

  onInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const rawDigits = input.value.replace(/\D/g, '').slice(0, TOTAL_DIGITS);
    this.digits.set(rawDigits);

    // On ne réécrit la valeur du champ que si elle contient autre chose que les chiffres bruts
    // (lettre tapée, collage avec espaces, dépassement de 18 chiffres) : dans le cas normal
    // (saisie chiffre par chiffre), input.value == rawDigits déjà, donc aucune réécriture ni
    // repositionnement de curseur — c'est ce qui rendait le champ inutilisable sur mobile.
    if (input.value !== rawDigits) {
      input.value = rawDigits;
    }
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

function formatWithPlaceholders(digits: string): string {
  let result = '';
  let index = 0;
  for (const size of GROUP_SIZES) {
    const group = digits.slice(index, index + size).padEnd(size, '_');
    result += (result ? ' ' : '') + group;
    index += size;
  }
  return result;
}
