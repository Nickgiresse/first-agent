import { Location } from '@angular/common';
import { errorMessage } from '../../../core/utils/error-message';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { NavigationService } from '../../../core/services/navigation';
import { OnboardingService } from '../../../core/services/onboarding';
import { OnboardingState } from '../../../core/services/onboarding-state';
import { LanguageService } from '../../../core/services/language';

@Component({
  selector: 'afb-pin-creation',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  templateUrl: './pin-creation.html',
  styleUrl: './pin-creation.scss',
})
export class PinCreation {
  private readonly service = inject(OnboardingService);
  private readonly navigation = inject(NavigationService);
  private readonly location = inject(Location);
  private readonly state = inject(OnboardingState);
  readonly lang = inject(LanguageService);
  readonly form = new FormGroup({
    pin: new FormControl('', {
      nonNullable: true,
      validators: [Validators.pattern(/^\d{4,6}$/), Validators.required],
    }),
    confirmation: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);
  goBack(): void {
    this.location.back();
  }
  digits(field: 'pin' | 'confirmation'): void {
    this.form.controls[field].setValue(
      this.form.controls[field].value.replace(/\D/g, '').slice(0, 6),
    );
  }
  submit(): void {
    if (this.form.invalid || this.form.value.pin !== this.form.value.confirmation) {
      this.error.set('Les deux codes PIN doivent être identiques et contenir 4 à 6 chiffres.');
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    // Conserve le PIN en clair pour la finalisation (transmis en HTTPS, haché côté banque).
    this.state.setPin(this.form.value.pin!);
    this.service
      .createProfile({
        pin: { pin: this.form.value.pin!, pinConfirmation: this.form.value.confirmation! },
      })
      .subscribe({
        next: () => {
          this.navigation.navigateTo('/onboarding/document-scan');
        },
        error: (error) => {
          this.error.set(
            errorMessage(error, 'Impossible de créer le code PIN.'),
          );
          this.submitting.set(false);
        },
      });
  }
}
