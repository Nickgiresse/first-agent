import { Location } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { OnboardingService } from '../../../core/services/onboarding';

@Component({ selector: 'app-pin-creation', imports: [ReactiveFormsModule], templateUrl: './pin-creation.html', styleUrl: './pin-creation.scss' })
export class PinCreation {
  private readonly service = inject(OnboardingService); private readonly router = inject(Router); private readonly location = inject(Location);
  readonly form = new FormGroup({ pin: new FormControl('', { nonNullable: true, validators: [Validators.pattern(/^\d{4,6}$/), Validators.required] }), confirmation: new FormControl('', { nonNullable: true, validators: [Validators.required] }) });
  readonly error = signal<string | null>(null); readonly submitting = signal(false);
  goBack(): void { this.location.back(); }
  digits(field: 'pin' | 'confirmation'): void { this.form.controls[field].setValue(this.form.controls[field].value.replace(/\D/g, '').slice(0, 6)); }
  submit(): void {
    if (this.form.invalid || this.form.value.pin !== this.form.value.confirmation) { this.error.set('Les deux codes PIN doivent être identiques et contenir 4 à 6 chiffres.'); return; }
    this.submitting.set(true); this.error.set(null);
    this.service.createProfile({ pin: { pin: this.form.value.pin!, pinConfirmation: this.form.value.confirmation! } }).subscribe({
      next: () => { this.router.navigateByUrl('/onboarding/document-scan'); },
      error: error => { this.error.set(error?.message ?? error?.error?.message ?? 'Impossible de créer le code PIN.'); this.submitting.set(false); }
    });
  }
}
