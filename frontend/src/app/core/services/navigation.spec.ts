import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { NavigationService } from './navigation';

/**
 * Ce service existe pour qu'un échec de navigation ne passe plus sous silence.
 *
 * Les dix appels du parcours ignoraient la promesse rendue par le routeur. Ces
 * cas vérifient que le remplacement fait bien ce pour quoi il a été écrit :
 * naviguer, et signaler quand cela n'aboutit pas.
 */
describe('NavigationService', () => {
  let service: NavigationService;
  let navigateByUrl: ReturnType<typeof vi.fn>;
  let erreurs: string[];

  beforeEach(() => {
    navigateByUrl = vi.fn();
    erreurs = [];
    vi.spyOn(console, 'error').mockImplementation((...args: unknown[]) => {
      erreurs.push(args.map(String).join(' '));
    });

    TestBed.configureTestingModule({
      providers: [{ provide: Router, useValue: { navigateByUrl } }],
    });
    service = TestBed.inject(NavigationService);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('navigue vers l\'adresse demandée', () => {
    navigateByUrl.mockResolvedValue(true);

    service.navigateTo('/onboarding/kyc');

    expect(navigateByUrl).toHaveBeenCalledWith('/onboarding/kyc');
  });

  it('ne signale rien quand la navigation aboutit', async () => {
    navigateByUrl.mockResolvedValue(true);

    service.navigateTo('/onboarding/kyc');
    await Promise.resolve();

    expect(erreurs).toHaveLength(0);
  });

  it('signale une navigation refusée', async () => {
    // Cas d'un garde qui rejette : le routeur rend `false` sans lever.
    // Auparavant, le client restait sur l'écran précédent sans que rien ne
    // soit tracé.
    navigateByUrl.mockResolvedValue(false);

    service.navigateTo('/onboarding/kyc');
    await Promise.resolve();

    expect(erreurs.join(' ')).toContain('/onboarding/kyc');
  });

  it('signale une navigation en erreur sans la propager', async () => {
    navigateByUrl.mockRejectedValue(new Error('module introuvable'));

    // Ne doit pas lever : l'appelant est un gestionnaire de succès HTTP, une
    // exception y remonterait comme un échec de l'opération métier.
    expect(() => service.navigateTo('/onboarding/kyc')).not.toThrow();
    await Promise.resolve();
    await Promise.resolve();

    expect(erreurs.join(' ')).toContain('module introuvable');
  });
});
