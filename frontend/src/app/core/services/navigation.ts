import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';

/**
 * Navigation du parcours d'onboarding.
 *
 * `Router.navigateByUrl` rend une promesse. L'ignorer, ce que faisaient les dix
 * appels du parcours, a deux conséquences.
 *
 * La première est technique : une navigation rejetée devient un rejet de
 * promesse non traité, que rien n'attrape.
 *
 * La seconde compte davantage. Une navigation peut échouer, sur un garde qui
 * refuse ou un module qui ne se charge pas. Le client reste alors sur l'écran
 * précédent, dont l'indicateur de chargement a déjà été éteint par le code qui
 * se croyait parti : il voit un formulaire figé, sans message, et n'a aucune
 * raison de comprendre qu'il doit recommencer. Côté exploitation, rien n'en
 * garde trace.
 *
 * Ce service centralise le traitement de ce cas. Il ne rend pas la navigation
 * plus fiable, il rend son échec visible.
 */
@Injectable({ providedIn: 'root' })
export class NavigationService {
  private readonly router = inject(Router);

  /**
   * Navigue vers une étape du parcours.
   *
   * L'appelant n'a pas à attendre le résultat : c'est bien une navigation sans
   * suite, et le service se charge de ne pas laisser un échec passer sous
   * silence.
   */
  navigateTo(url: string): void {
    this.router.navigateByUrl(url).then(
      abouti => {
        if (!abouti) {
          // Navigation refusée, par un garde le plus souvent. Ce n'est pas une
          // erreur technique, mais le client est resté où il était.
          this.signaler(url, 'refusée');
        }
      },
      raison => this.signaler(url, raison),
    );
  }

  private signaler(url: string, raison: unknown): void {
    // console.error et non console.log : la règle no-console l'autorise
    // précisément parce qu'il signale au lieu d'inspecter, et cela remonte
    // dans les outils de supervision du navigateur.
    console.error(`[NAVIGATION] Échec vers ${url} :`, raison);
  }
}
