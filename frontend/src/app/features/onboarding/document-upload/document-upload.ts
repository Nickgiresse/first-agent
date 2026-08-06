import { Location } from '@angular/common';
import { errorMessage } from '../../../core/utils/error-message';
import { NavigationService } from '../../../core/services/navigation';
import { Component, inject, signal } from '@angular/core';
import { DocumentService } from '../../../core/services/document';

@Component({
  selector: 'afb-document-upload',
  standalone: true,
  imports: [],
  templateUrl: './document-upload.html',
  styleUrl: './document-upload.scss'
})
export class DocumentUpload {
  private readonly navigation = inject(NavigationService);
  private readonly docs = inject(DocumentService);
  private readonly location = inject(Location);

  readonly front = signal<File | null>(null);
  readonly back = signal<File | null>(null);
  readonly frontPreview = signal<string | null>(null);
  readonly backPreview = signal<string | null>(null);

  readonly sending = signal(false);
  readonly statusMessage = signal<string>('Envoi...');
  readonly error = signal<string | null>(null);

  goBack(): void {
    this.location.back();
  }

  pick(event: Event, side: 'front' | 'back'): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    if (side === 'front') {
      this.front.set(file);
      if (file) {
        this.generatePreview(file, url => this.frontPreview.set(url));
      } else {
        this.frontPreview.set(null);
      }
    } else {
      this.back.set(file);
      if (file) {
        this.generatePreview(file, url => this.backPreview.set(url));
      } else {
        this.backPreview.set(null);
      }
    }
  }

  private generatePreview(file: File, callback: (url: string) => void): void {
    const reader = new FileReader();
    reader.onload = () => callback(reader.result as string);
    reader.readAsDataURL(file);
  }

  submit(): void {
    if (!this.front() || !this.back()) return;

    this.sending.set(true);
    this.error.set(null);
    this.statusMessage.set('Téléversement du recto...');

    this.docs.uploadDocument('CNI_RECTO', this.front()!).subscribe({
      next: () => {
        this.statusMessage.set('Téléversement du verso...');
        this.docs.uploadDocument('CNI_VERSO', this.back()!).subscribe({
          next: () => this.extract(),
          error: e => this.fail(e)
        });
      },
      error: e => this.fail(e)
    });
  }

  private extract(): void {
    this.statusMessage.set('Analyse qualité & extraction OCR par le moteur Python...');
    this.docs.extractOcrData().subscribe({
      next: () => this.navigation.navigateTo('/onboarding/document-ocr-review'),
      error: e => this.fail(e)
    });
  }

  private fail(e: unknown): void {
    const msg = errorMessage(e, 'Le traitement de la pièce d’identité a échoué.');
    this.error.set(msg);
    this.sending.set(false);
  }
}
