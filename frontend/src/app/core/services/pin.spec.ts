import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PinService } from './pin';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { PinResetConfirmRequest, PinResetResponse } from '../models/pin.model';

/**
 * Réinitialisation du code PIN : deux appels qui manipulent un secret bancaire.
 *
 * L'essentiel n'est pas que la requête parte, mais qu'elle parte au bon endroit
 * et par le bon canal. Un code ou un jeton de réinitialisation placé dans
 * l'adresse plutôt que dans le corps se retrouve dans l'historique du
 * navigateur, dans les journaux du reverse proxy et dans les traces d'accès :
 * la fuite est invisible côté client et permanente côté serveur.
 */
describe('PinService', () => {
  const URL_DEMANDE = `${environment.apiUrl}/pin/reset/request`;
  const URL_CONFIRMATION = `${environment.apiUrl}/pin/reset/confirm`;

  let service: PinService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PinService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function reponseDemande(): ApiResponse<PinResetResponse> {
    return {
      success: true,
      message: 'Demande enregistrée',
      data: { emailSent: true, requiresBranchVisit: false, message: 'Courriel envoyé' },
      timestamp: '2026-08-09T10:00:00Z',
    };
  }

  describe('requestReset', () => {
    it('poste le numéro de compte complet sur le chemin de demande', () => {
      service.requestReset('37100123456').subscribe();

      const requete = httpMock.expectOne(URL_DEMANDE);

      expect(requete.request.method).toBe('POST');
      // Le champ s'appelle `accountNumber` et attend le numéro entier, là où la
      // vérification de compte attend un `accountSuffix` de quelques chiffres.
      // Les deux services se ressemblent assez pour qu'une confusion passe la
      // compilation et parte en production.
      expect(requete.request.body).toEqual({ accountNumber: '37100123456' });

      requete.flush(reponseDemande());
    });

    it('ne place pas le numéro de compte dans l\'adresse', () => {
      service.requestReset('37100123456').subscribe();

      const requete = httpMock.expectOne(URL_DEMANDE);

      // Une adresse est journalisée partout ; un corps de requête ne l'est pas.
      expect(requete.request.urlWithParams).not.toContain('37100123456');

      requete.flush(reponseDemande());
    });

    it('transmet la réponse telle quelle, y compris un passage en agence imposé', () => {
      const attendu: ApiResponse<PinResetResponse> = {
        success: true,
        message: 'Réinitialisation impossible en ligne',
        data: {
          emailSent: false,
          requiresBranchVisit: true,
          message: 'Présentez-vous en agence avec une pièce d\'identité.',
        },
        timestamp: '2026-08-09T10:00:00Z',
      };
      let recu: ApiResponse<PinResetResponse> | undefined;

      service.requestReset('37100123456').subscribe((reponse) => (recu = reponse));
      httpMock.expectOne(URL_DEMANDE).flush(attendu);

      // `requiresBranchVisit` est une décision métier de la banque : la perdre
      // ferait attendre au client un courriel qui ne viendra jamais.
      expect(recu).toEqual(attendu);
    });

    it('laisse remonter un compte inconnu', () => {
      let statut: number | undefined;
      let succesAppele = false;

      service.requestReset('00000000000').subscribe({
        next: () => (succesAppele = true),
        error: (erreur: HttpErrorResponse) => (statut = erreur.status),
      });

      httpMock
        .expectOne(URL_DEMANDE)
        .flush({ message: 'Compte introuvable' }, { status: 404, statusText: 'Not Found' });

      expect(succesAppele).toBe(false);
      expect(statut).toBe(404);
    });
  });

  describe('confirmReset', () => {
    const demande: PinResetConfirmRequest = {
      resetToken: 'jeton-de-reinitialisation',
      newPin: '4821',
      newPinConfirmation: '4821',
    };

    it('poste la demande complète sur le chemin de confirmation', () => {
      service.confirmReset(demande).subscribe();

      const requete = httpMock.expectOne(URL_CONFIRMATION);

      expect(requete.request.method).toBe('POST');
      // La confirmation est envoyée telle quelle : c'est le serveur qui compare
      // les deux saisies. Filtrer `newPinConfirmation` côté front priverait la
      // banque de ce contrôle.
      expect(requete.request.body).toEqual(demande);

      requete.flush({ success: true, message: 'PIN modifié', data: null, timestamp: '' });
    });

    it("ne place ni le code ni le jeton dans l'adresse", () => {
      service.confirmReset(demande).subscribe();

      const requete = httpMock.expectOne(URL_CONFIRMATION);

      // Le cas qui compte le plus de ce fichier : un code PIN dans une chaîne
      // de requête est conservé par l'historique du navigateur et par tous les
      // journaux d'accès traversés, sans expiration.
      expect(requete.request.urlWithParams).toBe(URL_CONFIRMATION);
      expect(requete.request.urlWithParams).not.toContain('4821');
      expect(requete.request.urlWithParams).not.toContain('jeton-de-reinitialisation');

      requete.flush({ success: true, message: 'PIN modifié', data: null, timestamp: '' });
    });

    it('laisse remonter un jeton expiré au lieu de conclure au succès', () => {
      let statut: number | undefined;
      let message: string | undefined;
      let succesAppele = false;

      service.confirmReset(demande).subscribe({
        next: () => (succesAppele = true),
        error: (erreur: HttpErrorResponse) => {
          statut = erreur.status;
          message = erreur.error?.message;
        },
      });

      httpMock
        .expectOne(URL_CONFIRMATION)
        .flush(
          { message: 'Le lien de réinitialisation a expiré.' },
          { status: 410, statusText: 'Gone' },
        );

      // Annoncer un PIN modifié alors qu'il ne l'est pas laisserait le client
      // se croire à jour et échouer à sa prochaine opération.
      expect(succesAppele).toBe(false);
      expect(statut).toBe(410);
      expect(message).toBe('Le lien de réinitialisation a expiré.');
    });
  });
});
