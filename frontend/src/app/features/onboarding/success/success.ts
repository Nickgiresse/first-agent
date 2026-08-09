import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
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
  selector: 'afb-success',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  templateUrl: './success.html',
  styleUrl: './success.scss'
})
export class Success {
  readonly lang = inject(LanguageService);
  private readonly state = inject(OnboardingState);
  readonly whatsappUrl = environment.whatsappUrl;

  /**
   * Le lien de retour vers le bot est-il exploitable ?
   *
   * <p>`environment.prod.ts` porte encore `https://wa.me/`, sans numéro, en
   * attente de l'adresse définitive du service client. Tel quel, le seul bouton
   * de cet écran final mène à une page d'erreur WhatsApp, pour la totalité des
   * clients et au moment précis où le parcours vient d'aboutir.
   *
   * <p>Le lien est donc masqué tant que l'adresse est incomplète. Un écran de
   * réussite sans bouton de retour est décevant ; un bouton qui mène à une
   * erreur laisse croire que l'inscription a échoué.
   */
  readonly lienRetourDisponible = /wa\.me\/\d{6,}/.test(environment.whatsappUrl ?? '');

  constructor() {
    // Onboarding terminé : on purge l'état local, notamment le PIN et le token du lien
    // (déjà consommé côté banque) — ils ne doivent pas survivre au parcours.
    this.state.clear();
  }

  /**
   * Confettis de l'écran de réussite.
   *
   * `Math.random` est ici sans conséquence : ces valeurs ne pilotent que la
   * position, la couleur et la durée d'une animation décorative. Un aléatoire
   * prévisible ne dit rien du client et ne protège rien.
   *
   * L'exemption est posée au plus près, sur ces lignes seulement, et non dans
   * la configuration de l'analyseur : ailleurs dans une application bancaire un
   * générateur prévisible reste un défaut, et la règle doit continuer d'y
   * veiller.
   */
  /* eslint-disable sonarjs/pseudo-random */
  readonly confetti: ConfettiPiece[] = Array.from({ length: CONFETTI_COUNT }, () => ({
    left: Math.random() * 100,
    delay: Math.random() * 1.2,
    duration: 2.8 + Math.random() * 1.8,
    color: CONFETTI_COLORS[Math.floor(Math.random() * CONFETTI_COLORS.length)],
    rotation: Math.random() * 360,
    drift: (Math.random() - 0.5) * 120
  }));
  /* eslint-enable sonarjs/pseudo-random */
}
