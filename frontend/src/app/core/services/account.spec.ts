import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AccountService } from './account';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { AccountVerificationResponse } from '../models/account.model';

/**
 * Premier appel du parcours, et le seul qui se fasse sans session.
 *
 * Ce que ces cas verrouillent n'est pas le code du service, qui tient en trois
 * lignes, mais le contrat avec le backend : chemin, verbe et nom du champ. Une
 * divergence sur l'un des trois ne se voit pas à la compilation et se traduit
 * en production par un 404 ou un 400 devant lequel le client ne peut rien.
 */
describe('AccountService', () => {
  const URL_VERIFICATION = `${environment.apiUrl}/accounts/verify`;

  let service: AccountService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AccountService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    // Détecte les appels partis sans être attendus : un service qui doublonne
    // une requête de vérification déclencherait deux envois de jeton côté
    // banque.
    httpMock.verify();
  });

  function reponseValide(): ApiResponse<AccountVerificationResponse> {
    return {
      success: true,
      message: 'Compte éligible',
      data: {
        eligible: true,
        firstName: 'Jean',
        lastName: 'Nkeng',
        sessionToken: 'jeton-de-session',
        expiresInSeconds: 600,
      },
      timestamp: '2026-08-09T10:00:00Z',
    };
  }

  describe('verifyAccount', () => {
    it('poste le suffixe sur le chemin de vérification attendu par le backend', () => {
      service.verifyAccount('4567').subscribe();

      const requete = httpMock.expectOne(URL_VERIFICATION);

      expect(requete.request.method).toBe('POST');
      // Le nom du champ est le contrat. Le backend le lit tel quel ; le
      // renommer côté front donnerait un corps accepté par TypeScript et
      // rejeté par la validation serveur.
      expect(requete.request.body).toEqual({ accountSuffix: '4567' });

      requete.flush(reponseValide());
    });

    it("n'envoie pas de jeton de session, qui n'existe pas encore à cette étape", () => {
      service.verifyAccount('4567').subscribe();

      const requete = httpMock.expectOne(URL_VERIFICATION);

      // C'est cet appel qui crée la session. Y ajouter l'en-tête
      // d'authentification, par symétrie avec les autres services, enverrait
      // un jeton vide et ferait rejeter l'entrée du parcours.
      expect(requete.request.headers.has('X-Session-Token')).toBe(false);

      requete.flush(reponseValide());
    });

    it('transmet la réponse du serveur sans la déballer', () => {
      const attendu = reponseValide();
      let recu: ApiResponse<AccountVerificationResponse> | undefined;

      service.verifyAccount('4567').subscribe((reponse) => (recu = reponse));
      httpMock.expectOne(URL_VERIFICATION).flush(attendu);

      // Les appelants lisent `response.data.sessionToken`. Un service qui
      // déballerait l'enveloppe casserait tous les écrans d'un coup.
      expect(recu).toEqual(attendu);
    });

    it('laisse remonter un compte inéligible au lieu de le traiter comme un succès', () => {
      let statut: number | undefined;
      let message: string | undefined;
      let succesAppele = false;

      service.verifyAccount('0000').subscribe({
        next: () => (succesAppele = true),
        error: (erreur: HttpErrorResponse) => {
          statut = erreur.status;
          message = erreur.error?.message;
        },
      });

      httpMock.expectOne(URL_VERIFICATION).flush(
        { message: "Ce compte n'est pas éligible à l'ouverture en ligne." },
        { status: 422, statusText: 'Unprocessable Entity' },
      );

      // Le service ne doit rien avaler : c'est l'écran qui décide du message
      // affiché, et il ne peut le faire que si l'erreur lui parvient.
      expect(succesAppele).toBe(false);
      expect(statut).toBe(422);
      expect(message).toBe("Ce compte n'est pas éligible à l'ouverture en ligne.");
    });

    it('laisse remonter une coupure réseau', () => {
      let erreurRecue: HttpErrorResponse | undefined;

      service.verifyAccount('4567').subscribe({
        next: () => undefined,
        error: (erreur: HttpErrorResponse) => (erreurRecue = erreur),
      });

      httpMock.expectOne(URL_VERIFICATION).error(new ProgressEvent('error'));

      // Sans session, le client est bloqué à l'entrée : une erreur silencieuse
      // laisserait le bouton en chargement indéfiniment.
      expect(erreurRecue).toBeInstanceOf(HttpErrorResponse);
      expect(erreurRecue?.status).toBe(0);
    });
  });
});
