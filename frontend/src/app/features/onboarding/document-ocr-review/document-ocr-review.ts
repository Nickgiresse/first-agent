import { Location } from '@angular/common';
import { errorMessage } from '../../../core/utils/error-message';
import { NavigationService } from '../../../core/services/navigation';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DocumentService } from '../../../core/services/document';
import { DocumentKind } from '../../../core/models/ocr.model';
import { LanguageService } from '../../../core/services/language';

@Component({
  selector: 'afb-document-ocr-review',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  templateUrl: './document-ocr-review.html',
  styleUrl: './document-ocr-review.scss'
})
export class DocumentOcrReview implements OnInit {
  private readonly navigation = inject(NavigationService);
  readonly lang = inject(LanguageService);

  private readonly docs = inject(DocumentService);
  private readonly location = inject(Location);

  readonly documentKind = signal<DocumentKind>('UNKNOWN');

  goBack(): void {
    this.location.back();
  }

  readonly form = new FormGroup({
    firstName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    lastName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    documentNumber: new FormControl('', { nonNullable: true }),
    sex: new FormControl('', { nonNullable: true }),
    birthDate: new FormControl('', { nonNullable: true }),
    expiryDate: new FormControl('', { nonNullable: true }),
    birthPlace: new FormControl('', { nonNullable: true }),
    fatherName: new FormControl('', { nonNullable: true }),
    motherName: new FormControl('', { nonNullable: true }),
    kitNumber: new FormControl('', { nonNullable: true }),
    requestIdentifier: new FormControl('', { nonNullable: true }),
    paymentAmount: new FormControl('', { nonNullable: true }),
    paymentDate: new FormControl('', { nonNullable: true })
  });

  readonly confidenceScore = signal<number | null>(null);
  readonly qualityScore = signal<number | null>(null);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.docs.getOcrData().subscribe({
      next: response => {
        const data = response.data;
        this.documentKind.set(data.documentKind);
        if (data.confidenceScore != null) this.confidenceScore.set(Math.round(data.confidenceScore));
        if (data.documentQualityScore != null) this.qualityScore.set(Math.round(data.documentQualityScore));
        this.form.patchValue({
          firstName: data.firstName,
          lastName: data.lastName,
          documentNumber: data.documentNumber ?? '',
          sex: data.sex ?? '',
          birthDate: data.birthDate ?? '',
          expiryDate: data.expiryDate ?? '',
          birthPlace: data.birthPlace ?? '',
          fatherName: data.fatherName ?? '',
          motherName: data.motherName ?? '',
          kitNumber: data.kitNumber ?? '',
          requestIdentifier: data.requestIdentifier ?? '',
          paymentAmount: data.paymentAmount ?? '',
          paymentDate: data.paymentDate ?? ''
        });
        if (data.documentKind === 'CNI') {
          this.form.controls.documentNumber.addValidators(Validators.required);
          this.form.controls.documentNumber.updateValueAndValidity();
        }
        if (data.documentKind === 'RECEPISSE') {
          this.form.controls.paymentDate.addValidators(Validators.required);
          this.form.controls.paymentDate.updateValueAndValidity();
        } else {
          this.form.controls.birthDate.addValidators(Validators.required);
          this.form.controls.expiryDate.addValidators(Validators.required);
          this.form.controls.birthDate.updateValueAndValidity();
          this.form.controls.expiryDate.updateValueAndValidity();
        }
        this.loading.set(false);
      },
      error: error => {
        this.error.set(errorMessage(error, 'Impossible de récupérer les données extraites.'));
        this.loading.set(false);
      }
    });
  }

  submit(): void {
    if (this.form.invalid) return;

    this.submitting.set(true);
    this.error.set(null);
    this.docs.confirmOcrData(this.form.getRawValue()).subscribe({
      next: () => this.navigation.navigateTo('/onboarding/liveness-challenge'),
      error: error => {
        this.error.set(errorMessage(error, 'Impossible de confirmer ces informations.'));
        this.submitting.set(false);
      }
    });
  }
}
