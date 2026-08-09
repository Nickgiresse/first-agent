import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { onboardingGuard } from './onboarding-guard';
import { OnboardingState } from '../services/onboarding-state';

/**
 * Ce garde est la seule chose qui empêche d'atteindre une étape du parcours
 * sans session valide.
 *
 * Le contourner ne produit pas d'erreur visible : l'écran s'affiche, puis les
 * appels partent avec un jeton vide et le client se retrouve devant des refus
 * incompréhensibles au milieu d'un dossier bancaire. Les cas ci-dessous
 * verrouillent les deux issues du garde, et la forme de la seconde, car rendre
 * `false` après avoir navigué soi-même donnerait le même écran à l'oeil nu
 * tout en lançant deux navigations concurrentes.
 */
describe('onboardingGuard', () => {
  let isSessionValid: ReturnType<typeof vi.fn>;
  let router: Router;

  // Le garde n'utilise ni la route ni l'état du routeur : des valeurs vides
  // suffisent, et leur présence documente qu'aucune décision n'en dépend.
  const route = {} as ActivatedRouteSnapshot;
  const etatRouteur = { url: '/onboarding/kyc' } as RouterStateSnapshot;

  function executerGarde(): boolean | UrlTree {
    return TestBed.runInInjectionContext(
      () => onboardingGuard(route, etatRouteur),
    ) as boolean | UrlTree;
  }

  beforeEach(() => {
    isSessionValid = vi.fn();

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: OnboardingState, useValue: { isSessionValid } },
      ],
    });
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('session valide', () => {
    it("laisse passer, et rend exactement `true`", () => {
      isSessionValid.mockReturnValue(true);

      // L'égalité stricte compte : un `UrlTree` est lui aussi « truthy », donc
      // un test de vérité laisserait passer une redirection prise pour une
      // autorisation.
      expect(executerGarde()).toBe(true);
    });
  });

  describe('session absente ou expirée', () => {
    it('renvoie vers la vérification de compte, seul point d\'entrée du parcours', () => {
      isSessionValid.mockReturnValue(false);

      const resultat = executerGarde();

      expect(resultat).toBeInstanceOf(UrlTree);
      // On sérialise plutôt que de comparer des objets : c'est l'adresse
      // atteinte par le client qui compte, et une faute de frappe dans le
      // segment mènerait à une page inexistante sans que rien ne le signale.
      expect(router.serializeUrl(resultat as UrlTree)).toBe('/onboarding/account-verification');
    });

    it('ne rend jamais `false`, qui laisserait le client sur un écran mort', () => {
      isSessionValid.mockReturnValue(false);

      // Rendre `false` annule la navigation sans en proposer d'autre : le
      // client reste sur la page précédente, ou sur une page blanche s'il
      // arrivait par un lien externe.
      expect(executerGarde()).not.toBe(false);
    });

    it('laisse le routeur enchaîner au lieu de naviguer lui-même', () => {
      const navigate = vi.spyOn(router, 'navigate');
      const navigateByUrl = vi.spyOn(router, 'navigateByUrl');
      isSessionValid.mockReturnValue(false);

      executerGarde();

      // Naviguer depuis le garde déclencherait une seconde navigation en
      // concurrence de celle qu'il est en train d'annuler. Le résultat est le
      // même la plupart du temps, ce qui rend la régression invisible jusqu'au
      // jour où l'ordre s'inverse.
      expect(navigate).not.toHaveBeenCalled();
      expect(navigateByUrl).not.toHaveBeenCalled();
    });
  });

  describe('réévaluation', () => {
    it("interroge l'état à chaque activation plutôt que de mémoriser la première réponse", () => {
      isSessionValid.mockReturnValue(true);
      expect(executerGarde()).toBe(true);

      // La session expire au milieu du parcours : le garde suivant doit le
      // voir. Une valeur capturée à la création laisserait l'accès ouvert
      // jusqu'au rechargement de la page.
      isSessionValid.mockReturnValue(false);
      expect(executerGarde()).toBeInstanceOf(UrlTree);

      expect(isSessionValid).toHaveBeenCalledTimes(2);
    });
  });
});
