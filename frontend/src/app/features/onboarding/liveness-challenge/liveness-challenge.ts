import { Location } from '@angular/common';
import { errorMessage } from '../../../core/utils/error-message';
import { NavigationService } from '../../../core/services/navigation';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, afterNextRender, computed, inject, signal, viewChild } from '@angular/core';
import { CameraService } from '../../../core/services/camera';
import { DocumentService } from '../../../core/services/document';
import { computeCoverSourceRect } from '../../../core/services/image-quality-analyzer';
import { LIVENESS_ACTION_LABELS, LivenessAction } from '../../../core/models/liveness.model';
import { LanguageService } from '../../../core/services/language';

type Phase = 'LOADING' | 'READY' | 'CAPTURING' | 'VERIFYING' | 'RETRY' | 'FINALIZING' | 'ERROR';

const FRAME_COUNT = 8;
const FRAME_INTERVAL_MS = 200;
const PRE_CAPTURE_DELAY_MS = 800; // laisse le temps à la personne de commencer le mouvement
const CAPTURE_SIZE = 480; // carré, suffisant pour un visage

// Au delà, s'acharner sur la même épreuve ne mène nulle part : la cause est
// ailleurs, dans l'éclairage ou le cadrage.
const ECHECS_AVANT_REPRISE = 3;

@Component({
  selector: 'afb-liveness-challenge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  templateUrl: './liveness-challenge.html',
  styleUrl: './liveness-challenge.scss'
})
export class LivenessChallenge {
  private readonly navigation = inject(NavigationService);
  readonly lang = inject(LanguageService);

  private readonly camera = inject(CameraService);
  private readonly docs = inject(DocumentService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly location = inject(Location);

  readonly videoRef = viewChild<ElementRef<HTMLVideoElement>>('videoRef');
  readonly captureCanvasRef = viewChild<ElementRef<HTMLCanvasElement>>('captureCanvas');

  readonly phase = signal<Phase>('LOADING');
  readonly cameraError = this.camera.error;
  readonly error = signal<string | null>(null);
  readonly actions = signal<LivenessAction[]>([]);
  readonly currentActionIndex = signal(0);
  readonly actionLabels = LIVENESS_ACTION_LABELS;

  private firstFrameBlob: Blob | null = null;

  /**
   * Le composant est-il encore vivant ?
   *
   * <p>La boucle de capture dure près de deux secondes. Sans ce drapeau, un
   * client qui quitte l'écran pendant ce temps laissait la boucle tourner sur
   * un flux déjà arrêté, puis envoyer au serveur des images prises après coup.
   */
  private detruit = false;

  /**
   * Échecs consécutifs sur la même action.
   *
   * <p>Le serveur applique sa propre limite ; celle-ci ne la remplace pas. Elle
   * évite qu'un client rejoue indéfiniment la même épreuve sans jamais
   * comprendre qu'il doit changer quelque chose, et propose de reprendre depuis
   * le début plutôt que de s'acharner.
   */
  readonly echecsConsecutifs = signal(0);

  readonly currentAction = computed<LivenessAction | null>(
    () => this.actions()[this.currentActionIndex()] ?? null
  );

  constructor() {
    afterNextRender(() => void this.init());
    this.destroyRef.onDestroy(() => {
      // L'ordre compte peu, mais les deux sont nécessaires : arrêter le flux
      // sans lever le drapeau laisserait la boucle de capture continuer et
      // envoyer des images vides.
      this.detruit = true;
      this.camera.stop();
    });
  }

  startCapture(): void {
    // Deux gardes, dans cet ordre.
    //
    // La phase d'abord : sans elle, deux clics lançaient deux salves sur la
    // même action, et CHAQUE réponse incrémentait l'index. Une action du défi
    // n'était donc jamais demandée, et le parcours se terminait avec une
    // épreuve de moins que ce que le serveur avait tiré. C'était le
    // contournement le plus direct de la protection contre la photo imprimée.
    //
    // L'action ensuite, et avant de changer de phase : poser « CAPTURING »
    // puis sortir laissait l'écran figé sur une capture qui n'a jamais
    // commencé, sans erreur ni bouton.
    if (this.phase() !== 'READY' || !this.currentAction()) {
      return;
    }
    this.phase.set('CAPTURING');
    void this.captureAndVerify();
  }

  retryCurrentAction(): void {
    this.error.set(null);
    this.phase.set('READY');
  }

  goBack(): void {
    this.location.back();
  }

  /**
   * Relance la vérification depuis le début.
   *
   * <p>Distinct de {@link retryCurrentAction}, qui rejoue la seule action en
   * cours. Quand c'est le démarrage qui a échoué, il n'y a aucune action à
   * rejouer : la phase ERROR n'offrait alors qu'un message, sans issue.
   */
  restartChallenge(): void {
    this.error.set(null);
    this.actions.set([]);
    this.currentActionIndex.set(0);
    this.firstFrameBlob = null;
    this.echecsConsecutifs.set(0);
    this.phase.set('LOADING');
    void this.init();
  }

  private async init(): Promise<void> {
    const stream = await this.camera.start('user');
    const video = this.videoRef()?.nativeElement;
    if (!stream || !video) {
      // Sortir en silence laissait l'écran figé sur « Chargement » pour
      // toujours, sans message et sans bouton : le client qui refuse
      // l'autorisation par erreur, puis l'accorde, n'avait aucun moyen de
      // relancer. `cameraError` porte déjà le motif du refus.
      this.phase.set('ERROR');
      this.error.set(
        this.camera.error() ??
          'La caméra n’a pas pu être ouverte. Autorisez son accès puis relancez la vérification.'
      );
      return;
    }
    video.srcObject = stream;
    await video.play();

    this.docs.startLivenessChallenge().subscribe({
      next: response => {
        this.actions.set(response.data.actions);
        this.phase.set('READY');
      },
      error: err => this.fail(err)
    });
  }

  private async captureAndVerify(): Promise<void> {
    const action = this.currentAction();
    if (!action) return;

    await this.delay(PRE_CAPTURE_DELAY_MS);
    if (this.detruit) {
      return;
    }

    const frames: Blob[] = [];
    for (let i = 0; i < FRAME_COUNT; i++) {
      const blob = await this.captureFrame();
      if (blob) {
        frames.push(blob);
        this.firstFrameBlob ??= blob;
      }
      await this.delay(FRAME_INTERVAL_MS);
      // Vérifié à chaque tour et non une fois pour toutes : la boucle dure près
      // de deux secondes, et le client peut quitter l'écran à n'importe quel
      // moment. Sans cela, elle continuait sur un flux déjà arrêté et envoyait
      // au serveur des images prises après coup.
      if (this.detruit) {
        return;
      }
    }

    if (frames.length === 0) {
      this.fail(null);
      return;
    }

    this.phase.set('VERIFYING');
    this.docs.verifyLivenessAction(action, frames).subscribe({
      next: response => {
        if (!response.data.actionCompleted) {
          this.echecsConsecutifs.update(n => n + 1);
          if (this.echecsConsecutifs() >= ECHECS_AVANT_REPRISE) {
            // S'acharner sur la même épreuve ne mène nulle part : au bout de
            // trois refus, la cause est ailleurs, dans l'éclairage ou le
            // cadrage. Reprendre depuis le début tire un nouveau défi.
            this.error.set(
              'La vérification n’aboutit pas. Placez-vous face à une lumière ' +
                'douce, retirez lunettes et masque, puis reprenez la vérification.'
            );
            this.phase.set('ERROR');
            return;
          }
          this.phase.set('RETRY');
          return;
        }
        this.echecsConsecutifs.set(0);
        if (response.data.allActionsCompleted) {
          void this.finalize();
        } else {
          this.currentActionIndex.update(i => i + 1);
          this.phase.set('READY');
        }
      },
      error: err => this.fail(err)
    });
  }

  private captureFrame(): Promise<Blob | null> {
    const video = this.videoRef()?.nativeElement;
    const canvas = this.captureCanvasRef()?.nativeElement;
    if (!video || !canvas) return Promise.resolve(null);

    const { sx, sy, sw, sh } = computeCoverSourceRect(video.videoWidth, video.videoHeight, CAPTURE_SIZE, CAPTURE_SIZE);
    const context = canvas.getContext('2d');
    if (!context) return Promise.resolve(null);
    context.drawImage(video, sx, sy, sw, sh, 0, 0, CAPTURE_SIZE, CAPTURE_SIZE);

    return new Promise(resolve => canvas.toBlob(blob => resolve(blob), 'image/jpeg', 0.85));
  }

  private delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  private async finalize(): Promise<void> {
    if (!this.firstFrameBlob) {
      this.fail(null);
      return;
    }
    this.phase.set('FINALIZING');

    // La caméra n'est PAS coupée ici. Elle l'était, et « Réessayer » après un
    // échec d'envoi ou de comparaison recapturait alors sur un flux éteint :
    // aucune image ne sortait, et le client tournait en rond. Elle est libérée
    // une fois la vérification faciale aboutie, et de toute façon à la
    // destruction du composant.
    const selfieFile = new File([this.firstFrameBlob], 'selfie.jpg', { type: 'image/jpeg' });
    this.docs.uploadDocument('SELFIE', selfieFile).subscribe({
      next: () => this.verifyFace(),
      error: err => {
        // Reprise de parcours : le selfie a déjà été déposé lors d'une tentative
        // précédente. Le test portait sur la sous-chaîne « déjà téléversé »,
        // qui ne correspondait ni à la tournure naturelle « a déjà été
        // téléversé », ni à aucun message actuel du backend, celui-ci
        // remplaçant désormais le document existant. Le code de statut est le
        // seul repère stable : 409 dit « existe déjà », quelle que soit la
        // formulation et quelle que soit la langue.
        if (err?.status === 409) {
          this.verifyFace();
          return;
        }
        this.fail(err);
      }
    });
  }

  private verifyFace(): void {
    this.docs.verifyFace().subscribe({
      next: () => {
        // Le flux n'a plus d'utilité : le libérer éteint le voyant de la caméra
        // et rend l'objectif aux autres applications.
        this.camera.stop();
        this.navigation.navigateTo('/onboarding/terms-conditions');
      },
      error: err => this.fail(err)
    });
  }

  private fail(err: unknown): void {
    const message = errorMessage(err, 'La vérification de vivacité a échoué.');
    this.error.set(message);

    // Un défi jamais démarré ne se « réessaie » pas action par action : sans
    // action reçue, « Réessayer » puis « Commencer » ne menait nulle part. La
    // phase ERROR, elle, propose de reprendre depuis le début.
    this.phase.set(this.actions().length === 0 ? 'ERROR' : 'RETRY');
  }
}
