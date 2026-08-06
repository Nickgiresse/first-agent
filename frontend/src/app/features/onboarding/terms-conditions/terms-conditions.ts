import { Location } from '@angular/common';
import { NavigationService } from '../../../core/services/navigation';
import { Component, inject, signal } from '@angular/core';
import { OnboardingService } from '../../../core/services/onboarding';
import { LanguageService } from '../../../core/services/language';

/**
 * Écran d'acceptation des CGU.
 *
 * Trois exigences des directions (CGU v1.0 art. 4/12/13, CDC Onboarding v2 §Étape 5) sont
 * appliquées ici :
 *  1. lecture effective : la case reste inactive tant que le texte n'a pas été déroulé jusqu'au
 *     bout (défilement contrôlé), et non simplement déclarée ;
 *  2. consentement biométrique DISTINCT du consentement CGU général (donnée sensible) ;
 *  3. mentions obligatoires affichées (anti-phishing, finalité biométrique, contact DPO).
 */
@Component({
  selector: 'afb-terms-conditions',
  imports: [],
  templateUrl: './terms-conditions.html',
  styleUrl: './terms-conditions.scss'
})
export class TermsConditions {
  private readonly navigation = inject(NavigationService);
  private readonly service = inject(OnboardingService);
  private readonly location = inject(Location);
  readonly lang = inject(LanguageService);

  readonly accepted = signal(false);
  readonly biometricConsent = signal(false);
  readonly scrolledToEnd = signal(false);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  goBack(): void {
    this.location.back();
  }

  /** Marque la lecture comme effective quand le bas du texte est atteint (tolérance 24 px). */
  onTermsScroll(event: Event): void {
    const el = event.target as HTMLElement;
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 24) {
      this.scrolledToEnd.set(true);
    }
  }

  canSubmit(): boolean {
    return this.scrolledToEnd() && this.accepted() && this.biometricConsent() && !this.busy();
  }

  submit(): void {
    if (!this.canSubmit()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.service.acceptTerms({ termsAccepted: true }).subscribe({
      next: () => this.service.completeOnboarding().subscribe({
        next: () => this.navigation.navigateTo('/onboarding/success'),
        error: err => {
          // Échec de finalisation (ex. écriture refusée côté banque) : le dossier reste intact,
          // le client peut réessayer sans recommencer le parcours.
          this.error.set(err?.error?.message ?? 'Finalisation impossible pour le moment. Réessayez.');
          this.busy.set(false);
        }
      }),
      error: err => {
        this.error.set(err?.error?.message ?? 'Impossible d’enregistrer votre acceptation.');
        this.busy.set(false);
      }
    });
  }
}
