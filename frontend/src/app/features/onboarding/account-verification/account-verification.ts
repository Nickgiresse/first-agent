import { Location } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AccountService } from '../../../core/services/account';
import { OnboardingState } from '../../../core/services/onboarding-state';
import { LanguageService } from '../../../core/services/language';

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

  onInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const inputType = (event as InputEvent).inputType;

    // Position du curseur exprimée en nombre de chiffres (et non en caractères) : c'est la seule
    // référence stable quand on réécrit le champ, puisque les séparateurs se déplacent.
    const cursor = input.selectionStart ?? input.value.length;
    let digitsBeforeCursor = countDigits(input.value.slice(0, cursor));

    let rawDigits = input.value.replace(/\D/g, '').slice(0, TOTAL_DIGITS);

    // Retour arrière sur un séparateur : le nombre de chiffres n'a pas bougé, seul l'espace a
    // disparu. Sans ce traitement la frappe paraîtrait bloquée — on supprime donc le chiffre
    // qui précède, ce que l'utilisateur voulait faire.
    if (inputType === 'deleteContentBackward'
        && rawDigits.length === this.digits().length
        && digitsBeforeCursor > 0) {
      rawDigits = rawDigits.slice(0, digitsBeforeCursor - 1) + rawDigits.slice(digitsBeforeCursor);
      digitsBeforeCursor -= 1;
    }

    this.digits.set(rawDigits);

    // Le champ affiche les groupes 5-11-2 pendant la frappe ; la valeur transmise au backend
    // (voir submit()) reste les 18 chiffres bruts.
    const formatted = formatGroups(rawDigits);
    if (input.value !== formatted) {
      input.value = formatted;
      const position = positionAfterDigits(formatted, digitsBeforeCursor);
      input.setSelectionRange(position, position);
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

/** « 000010000007582781 » → « 00001 00000075827 81 » (groupes remplis au fur et à mesure). */
function formatGroups(digits: string): string {
  const groups: string[] = [];
  let index = 0;
  for (const size of GROUP_SIZES) {
    if (index >= digits.length) break;
    groups.push(digits.slice(index, index + size));
    index += size;
  }
  return groups.join(' ');
}

function countDigits(text: string): number {
  return (text.match(/\d/g) ?? []).length;
}

/** Index, dans la chaîne formatée, situé juste après le n-ième chiffre. */
function positionAfterDigits(formatted: string, digitCount: number): number {
  if (digitCount <= 0) return 0;
  let seen = 0;
  for (let i = 0; i < formatted.length; i++) {
    if (formatted[i] >= '0' && formatted[i] <= '9') {
      seen += 1;
      if (seen === digitCount) return i + 1;
    }
  }
  return formatted.length;
}
