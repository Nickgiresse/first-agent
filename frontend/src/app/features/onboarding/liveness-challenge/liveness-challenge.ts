import { Location } from '@angular/common';
import { Component, DestroyRef, ElementRef, afterNextRender, computed, inject, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';
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

@Component({
  selector: 'app-liveness-challenge',
  imports: [],
  templateUrl: './liveness-challenge.html',
  styleUrl: './liveness-challenge.scss'
})
export class LivenessChallenge {
  readonly lang = inject(LanguageService);

  private readonly camera = inject(CameraService);
  private readonly docs = inject(DocumentService);
  private readonly router = inject(Router);
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

  readonly currentAction = computed<LivenessAction | null>(
    () => this.actions()[this.currentActionIndex()] ?? null
  );

  constructor() {
    afterNextRender(() => void this.init());
    this.destroyRef.onDestroy(() => this.camera.stop());
  }

  startCapture(): void {
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

  private async init(): Promise<void> {
    const stream = await this.camera.start('user');
    const video = this.videoRef()?.nativeElement;
    if (!stream || !video) return;
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

    const frames: Blob[] = [];
    for (let i = 0; i < FRAME_COUNT; i++) {
      const blob = await this.captureFrame();
      if (blob) {
        frames.push(blob);
        this.firstFrameBlob ??= blob;
      }
      await this.delay(FRAME_INTERVAL_MS);
    }

    if (frames.length === 0) {
      this.fail(null);
      return;
    }

    this.phase.set('VERIFYING');
    this.docs.verifyLivenessAction(action, frames).subscribe({
      next: response => {
        if (!response.data.actionCompleted) {
          this.phase.set('RETRY');
          return;
        }
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
    this.camera.stop();

    const selfieFile = new File([this.firstFrameBlob], 'selfie.jpg', { type: 'image/jpeg' });
    this.docs.uploadDocument('SELFIE', selfieFile).subscribe({
      next: () => this.verifyFace(),
      error: err => {
        const message = err?.error?.message ?? err?.message ?? '';
        if (typeof message === 'string' && message.includes('déjà téléversé')) {
          this.verifyFace();
          return;
        }
        this.fail(err);
      }
    });
  }

  private verifyFace(): void {
    this.docs.verifyFace().subscribe({
      next: () => this.router.navigateByUrl('/onboarding/terms-conditions'),
      error: err => this.fail(err)
    });
  }

  private fail(err: unknown): void {
    const message = (err as any)?.error?.message ?? (err as any)?.message ?? 'La vérification de vivacité a échoué.';
    this.error.set(message);
    this.phase.set('RETRY');
  }
}
