import { Location } from '@angular/common';
import { errorMessage } from '../../../core/utils/error-message';
import { NavigationService } from '../../../core/services/navigation';
import { Component, DestroyRef, ElementRef, afterNextRender, inject, signal, viewChild } from '@angular/core';
import { CameraService } from '../../../core/services/camera';
import { DocumentService } from '../../../core/services/document';
import { QualityReport, analyzeImageQuality, computeCoverSourceRect } from '../../../core/services/image-quality-analyzer';
import { LanguageService } from '../../../core/services/language';

type CaptureStep = 'FRONT' | 'BACK' | 'UPLOADING';

const ANALYSIS_INTERVAL_MS = 350;
const STABILITY_TICKS_REQUIRED = 3;
// Ratio 4:3, aligné sur .scan-viewport { aspect-ratio: 4/3 } (document-scan.scss) pour
// que l'image analysée corresponde à ce que l'utilisateur voit réellement à l'écran.
const ANALYSIS_WIDTH = 200;
const ANALYSIS_HEIGHT = 150;

@Component({
  selector: 'afb-document-scan',
  imports: [],
  templateUrl: './document-scan.html',
  styleUrl: './document-scan.scss'
})
export class DocumentScan {
  private readonly navigation = inject(NavigationService);
  readonly lang = inject(LanguageService);

  private readonly camera = inject(CameraService);
  private readonly docs = inject(DocumentService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly location = inject(Location);

  readonly videoRef = viewChild<ElementRef<HTMLVideoElement>>('videoRef');
  readonly analysisCanvasRef = viewChild<ElementRef<HTMLCanvasElement>>('analysisCanvas');

  readonly step = signal<CaptureStep>('FRONT');
  readonly feedbackMessage = signal('Positionnez le recto de votre CNI dans le cadre.');
  readonly cameraError = this.camera.error;
  readonly error = signal<string | null>(null);

  private frontBlob: Blob | null = null;
  private backBlob: Blob | null = null;
  private stableTicks = 0;
  private lastAnalysisAt = 0;
  private analysisFrameId: number | null = null;
  private active = false;

  constructor() {
    afterNextRender(() => void this.initCamera());
    this.destroyRef.onDestroy(() => this.pauseCapture());
  }

  useManualUpload(): void {
    this.pauseCapture();
    this.navigation.navigateTo('/onboarding/document-upload');
  }

  goBack(): void {
    this.location.back();
  }

  private async initCamera(): Promise<void> {
    const stream = await this.camera.start();
    const video = this.videoRef()?.nativeElement;
    if (!stream || !video) return;

    video.srcObject = stream;
    await video.play();
    this.active = true;
    this.analysisFrameId = requestAnimationFrame(this.analyzeFrame);
  }

  private analyzeFrame = (timestamp: number): void => {
    if (!this.active) return;
    this.analysisFrameId = requestAnimationFrame(this.analyzeFrame);

    if (timestamp - this.lastAnalysisAt < ANALYSIS_INTERVAL_MS) return;
    this.lastAnalysisAt = timestamp;

    const video = this.videoRef()?.nativeElement;
    const canvas = this.analysisCanvasRef()?.nativeElement;
    if (!video || !canvas || video.readyState < 2) return;

    const context = canvas.getContext('2d', { willReadFrequently: true });
    if (!context) return;

    const { sx, sy, sw, sh } = computeCoverSourceRect(
      video.videoWidth,
      video.videoHeight,
      ANALYSIS_WIDTH,
      ANALYSIS_HEIGHT
    );
    context.drawImage(video, sx, sy, sw, sh, 0, 0, ANALYSIS_WIDTH, ANALYSIS_HEIGHT);
    const imageData = context.getImageData(0, 0, ANALYSIS_WIDTH, ANALYSIS_HEIGHT);
    const report = analyzeImageQuality(imageData);

    this.feedbackMessage.set(this.describe(report));

    if (report.overallOk) {
      this.stableTicks++;
      if (this.stableTicks >= STABILITY_TICKS_REQUIRED) {
        this.stableTicks = 0;
        this.capture(video);
      }
    } else {
      this.stableTicks = 0;
    }
  };

  private describe(report: QualityReport): string {
    const face = this.step() === 'FRONT' ? 'le recto' : 'le verso';
    if (!report.framed) return `Placez ${face} de votre CNI entièrement dans le cadre.`;
    if (report.glareDetected) return 'Un reflet est détecté, inclinez légèrement le document.';
    if (report.brightness < 60) return 'Il fait trop sombre, rapprochez-vous d’une source de lumière.';
    if (report.brightness > 200) return 'La lumière est trop forte, évitez la lumière directe.';
    if (report.sharpness < 12) return 'Image floue, stabilisez votre appareil.';
    return 'Parfait, ne bougez plus…';
  }

  private capture(video: HTMLVideoElement): void {
    const { sx, sy, sw, sh } = computeCoverSourceRect(video.videoWidth, video.videoHeight, 4, 3);
    const canvas = document.createElement('canvas');
    canvas.width = sw;
    canvas.height = sh;
    const context = canvas.getContext('2d');
    if (!context) return;
    context.drawImage(video, sx, sy, sw, sh, 0, 0, canvas.width, canvas.height);

    canvas.toBlob(blob => {
      if (!blob) return;
      if (this.step() === 'FRONT') {
        this.frontBlob = blob;
        this.step.set('BACK');
        this.feedbackMessage.set('Recto capturé. Retournez la CNI et présentez le verso.');
      } else {
        this.backBlob = blob;
        this.upload();
      }
    }, 'image/jpeg', 0.92);
  }

  private upload(): void {
    if (!this.frontBlob || !this.backBlob) {
      this.error.set('Votre session a expiré. Recommencez la vérification du compte.');
      return;
    }
    this.pauseCapture();
    this.step.set('UPLOADING');
    this.feedbackMessage.set('Analyse des documents en cours…');

    const frontFile = new File([this.frontBlob], 'cni-recto.jpg', { type: 'image/jpeg' });
    const backFile = new File([this.backBlob], 'cni-verso.jpg', { type: 'image/jpeg' });

    this.docs.uploadDocument('CNI_RECTO', frontFile).subscribe({
      next: () => this.docs.uploadDocument('CNI_VERSO', backFile).subscribe({
        next: () => this.extract(),
        error: e => this.fail(e)
      }),
      error: e => this.fail(e)
    });
  }

  private extract(): void {
    this.docs.extractOcrData().subscribe({
      next: () => this.navigation.navigateTo('/onboarding/document-ocr-review'),
      error: e => this.fail(e)
    });
  }

  private fail(e: unknown): void {
    this.error.set(errorMessage(e, 'Le traitement du document a échoué.'));
    this.step.set('FRONT');
    this.frontBlob = null;
    this.backBlob = null;
    void this.initCamera();
  }

  private pauseCapture(): void {
    this.active = false;
    if (this.analysisFrameId !== null) {
      cancelAnimationFrame(this.analysisFrameId);
      this.analysisFrameId = null;
    }
    this.camera.stop();
  }
}
