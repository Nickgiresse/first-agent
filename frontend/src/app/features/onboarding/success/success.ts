import { Component, inject } from '@angular/core';
import { environment } from '../../../../environments/environment';
import { LanguageService } from '../../../core/services/language';
import { OnboardingState } from '../../../core/services/onboarding-state';

interface ConfettiPiece {
  left: number;
  delay: number;
  duration: number;
  color: string;
  rotation: number;
  drift: number;
}

const CONFETTI_COLORS = ['#e30613', '#ffb400', '#2ecc71', '#3498db', '#9b59b6', '#ff6f91'];
const CONFETTI_COUNT = 60;

@Component({
  selector: 'app-success',
  imports: [],
  templateUrl: './success.html',
  styleUrl: './success.scss'
})
export class Success {
  readonly lang = inject(LanguageService);
  private readonly state = inject(OnboardingState);
  readonly whatsappUrl = environment.whatsappUrl;

  constructor() {
    // Onboarding terminé : on purge l'état local, notamment le PIN et le token du lien
    // (déjà consommé côté banque) — ils ne doivent pas survivre au parcours.
    this.state.clear();
  }

  readonly confetti: ConfettiPiece[] = Array.from({ length: CONFETTI_COUNT }, () => ({
    left: Math.random() * 100,
    delay: Math.random() * 1.2,
    duration: 2.8 + Math.random() * 1.8,
    color: CONFETTI_COLORS[Math.floor(Math.random() * CONFETTI_COLORS.length)],
    rotation: Math.random() * 360,
    drift: (Math.random() - 0.5) * 120
  }));
}
