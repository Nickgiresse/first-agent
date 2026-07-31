import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class CameraService {
  readonly stream = signal<MediaStream | null>(null);
  readonly error = signal<string | null>(null);

  async start(facingMode: 'environment' | 'user' = 'environment'): Promise<MediaStream | null> {
    this.error.set(null);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode },
        audio: false
      });
      this.stream.set(stream);
      return stream;
    } catch {
      this.error.set('Impossible d’accéder à la caméra. Vérifiez les autorisations de votre navigateur.');
      this.stream.set(null);
      return null;
    }
  }

  stop(): void {
    this.stream()?.getTracks().forEach(track => track.stop());
    this.stream.set(null);
  }
}
