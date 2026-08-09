import { Location } from '@angular/common';
import { errorMessage } from '../../../core/utils/error-message';
import { NavigationService } from '../../../core/services/navigation';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DocumentService } from '../../../core/services/document';

/** Limite annoncée au client sur l'écran ; elle doit être tenue ici. */
const TAILLE_MAX_OCTETS = 5 * 1024 * 1024;

/**
 * Formats que le service de vision sait lire.
 *
 * <p>Liste blanche : l'inventaire de ce qu'il ne sait pas lire est sans fin.
 */
const TYPES_ACCEPTES = ['image/jpeg', 'image/png'];

@Component({
  selector: 'afb-document-upload',
  changeDetection: ChangeDetectionStrategy.OnPush,
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

    // L'erreur précédente ne porte plus sur ce qui est affiché : la laisser
    // ferait croire au client que sa correction est refusée elle aussi.
    this.error.set(null);

    // L'attribut `accept` du gabarit n'est qu'un filtre d'affichage dans la
    // boîte de dialogue : il est contourné par le glisser-déposer, et ne
    // garantit rien. Le contrôle doit donc exister ici, d'autant que l'écran
    // annonce au client une limite de 5 Mo qui n'était vérifiée nulle part.
    if (file && !this.fichierAcceptable(file)) {
      (event.target as HTMLInputElement).value = '';
      return;
    }

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

  /**
   * Le fichier choisi est-il exploitable ?
   *
   * <p>Refuser tôt évite au client d'attendre un téléversement de plusieurs
   * dizaines de mégaoctets pour se voir refuser à l'arrivée, et évite au
   * service de vision de recevoir un contenu qu'il ne sait pas lire.
   *
   * <p>Le message dit ce qui ne va pas et ce qu'il faut faire : « fichier
   * invalide » laisserait le client rechoisir la même photo.
   */
  private fichierAcceptable(file: File): boolean {
    if (!TYPES_ACCEPTES.includes(file.type)) {
      this.error.set(
        'Ce format n’est pas accepté. Choisissez une photo au format JPEG ou PNG.'
      );
      return false;
    }
    if (file.size > TAILLE_MAX_OCTETS) {
      const mega = (file.size / 1024 / 1024).toFixed(1);
      this.error.set(
        `Ce fichier pèse ${mega} Mo, au delà des 5 Mo autorisés. ` +
          'Reprenez la photo ou réduisez sa définition.'
      );
      return false;
    }
    if (file.size === 0) {
      // Un fichier vide passe les deux contrôles précédents et n'échoue qu'au
      // serveur, avec un message qui ne dit rien au client.
      this.error.set('Ce fichier est vide. Choisissez une autre photo.');
      return false;
    }
    return true;
  }

  private generatePreview(file: File, callback: (url: string) => void): void {
    const reader = new FileReader();
    reader.onload = () => callback(reader.result as string);
    reader.readAsDataURL(file);
  }

  submit(): void {
    // La garde sur `sending` n'est pas un doublon de l'attribut `disabled` du
    // gabarit : sur un téléphone lent, deux clics passent avant le premier
    // rendu, et les deux faces partiraient deux fois.
    if (!this.front() || !this.back() || this.sending()) {
      return;
    }

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
      next: () => {
        // Libéré avant la navigation : celle-ci peut être refusée, et le
        // client resterait sinon devant un écran d'envoi qui n'aboutit jamais.
        this.sending.set(false);
        this.navigation.navigateTo('/onboarding/document-ocr-review');
      },
      error: e => this.fail(e)
    });
  }

  private fail(e: unknown): void {
    const msg = errorMessage(e, 'Le traitement de la pièce d’identité a échoué.');
    this.error.set(msg);
    this.sending.set(false);
  }
}
