import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { OnboardingState } from '../../../core/services/onboarding-state';
import { OnboardingService } from '../../../core/services/onboarding';
import { LanguageService } from '../../../core/services/language';

@Component({ selector: 'app-welcome', imports: [RouterLink], templateUrl: './welcome.html', styleUrl: './welcome.scss' })
export class Welcome implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly state = inject(OnboardingState);
  private readonly service = inject(OnboardingService);
  readonly lang = inject(LanguageService);

  // Nom déjà connu côté banque (si le lien est reconnu) — affichable par le template.
  readonly knownName = signal<string | null>(null);

  ngOnInit(): void {
    // Entrée par lien WhatsApp : le JWT arrive dans l'URL (?t=). On le conserve pour la
    // finalisation, puis on valide/pré-remplit sans bloquer le parcours en cas d'échec.
    const token = this.route.snapshot.queryParamMap.get('t');
    if (!token) {
      return;
    }
    this.state.setLinkToken(token);
    this.service.verifyLink(token).subscribe({
      next: res => {
        if (res?.data?.name) {
          this.knownName.set(res.data.name);
        }
        // Langue portée par le lien WhatsApp : le parcours s'ouvre dans la langue du client.
        this.lang.applyFromLink(res?.data?.lang);
      },
      error: () => {
        // Lien invalide ou expiré : le parcours standard (saisie du compte) reste accessible.
      }
    });
  }
}
