import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { OnboardingService } from './onboarding';
import { OnboardingState } from './onboarding-state';
import { environment } from '../../../environments/environment';

/**
 * Étapes déclaratives du parcours : courriel, code à usage unique, création du
 * profil, acceptation des CGU et finalisation.
 *
 * Deux points méritent d'être verrouillés au-delà du contrat d'adressage. Le
 * premier est l'entrée par lien, seul appel qui se fasse sans session : lui
 * ajouter l'en-tête d'authentification par symétrie enverrait un jeton vide et
 * fermerait la porte d'entrée du parcours. Le second est la finalisation, qui
 * embarque le code PIN et le jeton du lien lus dans l'état au moment de
 * l'appel : ce sont les deux seules valeurs qui ne peuvent pas être
 * reconstituées si elles se perdent.
 */
describe('OnboardingService', () => {
  const BASE = `${environment.apiUrl}/onboarding`;
  const JETON = 'jeton-de-session';

  let service: OnboardingService;
  let etat: OnboardingState;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OnboardingService);
    etat = TestBed.inject(OnboardingState);
    httpMock = TestBed.inject(HttpTestingController);

    etat.setAccountVerified(JETON, 'Jean', 'Nkeng', 600);
  });

  afterEach(() => {
    // Une finalisation partie deux fois créerait deux clients au core banking.
    httpMock.verify();
    sessionStorage.clear();
  });

  /**
   * Table des routes authentifiées. Écrite à plat pour que le déplacement d'un
   * chemin côté backend fasse échouer une ligne identifiable, plutôt que de se
   * traduire en 404 sur un écran quelconque.
   */
  const routes: ReadonlyArray<{ nom: string; url: string; appeler: () => void }> = [
    {
      nom: 'requestEmailOtp',
      url: `${BASE}/kyc/otp/request`,
      appeler: () => service.requestEmailOtp({ email: 'client@exemple.invalid' }).subscribe(),
    },
    {
      nom: 'verifyEmailOtp',
      url: `${BASE}/kyc/otp/verify`,
      appeler: () => service.verifyEmailOtp({ code: '123456' }).subscribe(),
    },
    {
      nom: 'createProfile',
      url: `${BASE}/profile`,
      appeler: () =>
        service.createProfile({ pin: { pin: '4821', pinConfirmation: '4821' } }).subscribe(),
    },
    {
      nom: 'acceptTerms',
      url: `${BASE}/terms`,
      appeler: () => service.acceptTerms({ termsAccepted: true }).subscribe(),
    },
    {
      nom: 'completeOnboarding',
      url: `${BASE}/complete`,
      appeler: () => service.completeOnboarding().subscribe(),
    },
  ];

  describe('adressage et authentification', () => {
    routes.forEach(({ nom, url, appeler }) => {
      it(`${nom} poste sur sa route en présentant le jeton de session`, () => {
        appeler();

        const requete = httpMock.expectOne(url);

        expect(requete.request.method).toBe('POST');
        expect(requete.request.headers.get('X-Session-Token')).toBe(JETON);

        requete.flush({ success: true, message: '', data: null, timestamp: '' });
      });
    });

    it('relit le jeton à chaque appel plutôt que de le figer', () => {
      service.acceptTerms({ termsAccepted: true }).subscribe();
      httpMock.expectOne(`${BASE}/terms`).flush({});

      etat.setAccountVerified('nouveau-jeton', 'Jean', 'Nkeng', 600);

      service.acceptTerms({ termsAccepted: true }).subscribe();
      const seconde = httpMock.expectOne(`${BASE}/terms`);
      // Une session rejouée en cours de parcours produit un nouveau jeton :
      // continuer avec l'ancien ferait échouer toutes les étapes suivantes avec
      // un message de session expirée alors que la session est valide.
      expect(seconde.request.headers.get('X-Session-Token')).toBe('nouveau-jeton');
      seconde.flush({});
    });
  });

  describe('requestEmailOtp', () => {
    it("transmet l'adresse saisie telle quelle", () => {
      service.requestEmailOtp({ email: 'Jean.Nkeng@Exemple.invalid' }).subscribe();

      const requete = httpMock.expectOne(`${BASE}/kyc/otp/request`);

      // Aucune normalisation ici : c'est le backend qui décide de la casse
      // retenue, et c'est à cette adresse exacte que le code sera envoyé.
      expect(requete.request.body).toEqual({ email: 'Jean.Nkeng@Exemple.invalid' });

      requete.flush({});
    });

    it("laisse remonter un envoi de courriel en échec", () => {
      let statut: number | undefined;
      let succesAppele = false;

      service.requestEmailOtp({ email: 'client@exemple.invalid' }).subscribe({
        next: () => (succesAppele = true),
        error: (erreur: HttpErrorResponse) => (statut = erreur.status),
      });

      httpMock
        .expectOne(`${BASE}/kyc/otp/request`)
        .flush({ message: 'Service de messagerie indisponible' }, { status: 502, statusText: 'Bad Gateway' });

      // Conclure au succès enverrait le client attendre un code qui n'arrivera
      // pas, sans moyen de relancer.
      expect(succesAppele).toBe(false);
      expect(statut).toBe(502);
    });
  });

  describe('verifyEmailOtp', () => {
    it('poste le code dans le corps et non dans l\'adresse', () => {
      service.verifyEmailOtp({ code: '123456' }).subscribe();

      const requete = httpMock.expectOne(`${BASE}/kyc/otp/verify`);

      // Un code à usage unique glissé dans une chaîne de requête se retrouve
      // dans l'historique du navigateur et dans les journaux d'accès, où il
      // reste valable le temps de sa fenêtre.
      expect(requete.request.body).toEqual({ code: '123456' });
      expect(requete.request.urlWithParams).not.toContain('123456');

      requete.flush({});
    });

    it('laisse remonter un code refusé', () => {
      let message: string | undefined;
      let succesAppele = false;

      service.verifyEmailOtp({ code: '000000' }).subscribe({
        next: () => (succesAppele = true),
        error: (erreur: HttpErrorResponse) => (message = erreur.error?.message),
      });

      httpMock
        .expectOne(`${BASE}/kyc/otp/verify`)
        .flush({ message: 'Code invalide ou expiré' }, { status: 400, statusText: 'Bad Request' });

      // Le point de contrôle du parcours : un code refusé pris pour un succès
      // validerait une adresse de courriel non prouvée.
      expect(succesAppele).toBe(false);
      expect(message).toBe('Code invalide ou expiré');
    });
  });

  describe('createProfile', () => {
    it('conserve le code PIN et sa confirmation imbriqués, comme attendu côté serveur', () => {
      service.createProfile({ pin: { pin: '4821', pinConfirmation: '4821' } }).subscribe();

      const requete = httpMock.expectOne(`${BASE}/profile`);

      // La structure est imbriquée volontairement : à plat, le serveur ne
      // trouverait pas ses champs et rejetterait la création avec une erreur de
      // validation illisible pour le client.
      expect(requete.request.body).toEqual({ pin: { pin: '4821', pinConfirmation: '4821' } });

      requete.flush({});
    });

    it('ne laisse pas le code PIN apparaître dans l\'adresse', () => {
      service.createProfile({ pin: { pin: '4821', pinConfirmation: '4821' } }).subscribe();

      const requete = httpMock.expectOne(`${BASE}/profile`);

      // Le PIN est un secret bancaire : le corps est chiffré par TLS et n'est
      // journalisé nulle part, l'adresse l'est partout.
      expect(requete.request.urlWithParams).toBe(`${BASE}/profile`);

      requete.flush({});
    });

    it('laisse remonter un code PIN rejeté par les règles de la banque', () => {
      let message: string | undefined;

      service.createProfile({ pin: { pin: '1111', pinConfirmation: '1111' } }).subscribe({
        next: () => undefined,
        error: (erreur: HttpErrorResponse) => (message = erreur.error?.message),
      });

      httpMock
        .expectOne(`${BASE}/profile`)
        .flush(
          { message: 'Le code ne doit pas être une suite de chiffres identiques.' },
          { status: 422, statusText: 'Unprocessable Entity' },
        );

      expect(message).toBe('Le code ne doit pas être une suite de chiffres identiques.');
    });
  });

  describe('acceptTerms', () => {
    it("transmet le refus aussi bien que l'acceptation", () => {
      service.acceptTerms({ termsAccepted: false }).subscribe();

      const requete = httpMock.expectOne(`${BASE}/terms`);

      // L'acceptation des CGU est une preuve à conserver : forcer `true` côté
      // client, ou omettre le champ quand il vaut `false`, ferait enregistrer
      // un consentement qui n'a pas été donné.
      expect(requete.request.body).toEqual({ termsAccepted: false });

      requete.flush({});
    });
  });

  describe('verifyLink', () => {
    it('poste le jeton du lien sur la route de vérification', () => {
      service.verifyLink('jwt-du-lien').subscribe();

      const requete = httpMock.expectOne(`${BASE}/link/verify`);

      expect(requete.request.method).toBe('POST');
      expect(requete.request.body).toEqual({ token: 'jwt-du-lien' });
      // Le jeton du lien identifie le client auprès du WhatsApp banking : dans
      // l'adresse, il serait rejouable par quiconque lit un journal.
      expect(requete.request.urlWithParams).not.toContain('jwt-du-lien');

      requete.flush({});
    });

    it("n'ajoute pas l'en-tête de session, qui n'existe pas encore", () => {
      etat.clear();

      service.verifyLink('jwt-du-lien').subscribe();
      const requete = httpMock.expectOne(`${BASE}/link/verify`);

      // C'est le seul appel du service qui précède la session. Lui appliquer
      // `authHeaders()` par uniformité enverrait un en-tête vide et pourrait
      // faire rejeter l'entrée par lien, c'est-à-dire le chemin par lequel
      // arrivent les clients venus du bot.
      expect(requete.request.headers.has('X-Session-Token')).toBe(false);

      requete.flush({});
    });

    it('transmet le contexte du client sans le réinterpréter', () => {
      const attendu = {
        success: true,
        message: '',
        data: {
          valid: true,
          phone: '+237690000000',
          name: 'Jean Nkeng',
          accountNumber: '37100123456',
          lang: 'en',
          alreadyOnboarded: false,
        },
        timestamp: '2026-08-09T10:00:00Z',
      };
      let recu: unknown;

      service.verifyLink('jwt-du-lien').subscribe((reponse) => (recu = reponse));
      httpMock.expectOne(`${BASE}/link/verify`).flush(attendu);

      // `alreadyOnboarded` et `lang` pilotent l'ouverture du parcours : le
      // premier évite de refaire un dossier existant, le second choisit la
      // langue d'accueil.
      expect(recu).toEqual(attendu);
    });

    it('laisse remonter un lien expiré', () => {
      let statut: number | undefined;

      service.verifyLink('jwt-perime').subscribe({
        next: () => undefined,
        error: (erreur: HttpErrorResponse) => (statut = erreur.status),
      });

      httpMock
        .expectOne(`${BASE}/link/verify`)
        .flush({ message: 'Lien expiré' }, { status: 401, statusText: 'Unauthorized' });

      // Sans remontée, le parcours démarrerait sur un contexte vide et le
      // client saisirait tout à la main sans comprendre pourquoi.
      expect(statut).toBe(401);
    });
  });

  describe('completeOnboarding', () => {
    it('embarque le jeton du lien et le code PIN portés par les étapes précédentes', () => {
      etat.setLinkToken('jwt-du-lien');
      etat.setPin('4821');

      service.completeOnboarding().subscribe();

      const requete = httpMock.expectOne(`${BASE}/complete`);

      // Ces deux valeurs ne sont demandées nulle part ailleurs : le PIN n'est
      // saisi qu'une fois et n'est pas persisté, le jeton du lien est consommé
      // par la source de vérité. Les oublier ici rend le dossier infinalisable
      // sans redémarrer tout le parcours.
      expect(requete.request.body).toEqual({ linkToken: 'jwt-du-lien', pin: '4821' });

      requete.flush({});
    });

    it('lit l\'état au moment de l\'appel et non à la construction du service', () => {
      // Le service est créé bien avant que le PIN soit saisi : une capture à la
      // construction enverrait systématiquement `null`.
      etat.setPin('4821');
      etat.setLinkToken('jwt-du-lien');

      service.completeOnboarding().subscribe();
      const premiere = httpMock.expectOne(`${BASE}/complete`);
      expect(premiere.request.body).toEqual({ linkToken: 'jwt-du-lien', pin: '4821' });
      premiere.flush({});

      etat.setPin('9999');
      service.completeOnboarding().subscribe();
      const seconde = httpMock.expectOne(`${BASE}/complete`);
      expect(seconde.request.body).toEqual({ linkToken: 'jwt-du-lien', pin: '9999' });
      seconde.flush({});
    });

    it('envoie des valeurs nulles pour un parcours entré sans lien', () => {
      service.completeOnboarding().subscribe();

      const requete = httpMock.expectOne(`${BASE}/complete`);

      // Entrée directe, sans passer par le bot : le backend doit voir des
      // champs présents et nuls, pas des champs absents, pour rester local au
      // lieu d'appeler le WhatsApp banking avec un jeton indéfini.
      expect(requete.request.body).toEqual({ linkToken: null, pin: null });

      requete.flush({});
    });

    it('ne place jamais le code PIN dans l\'adresse', () => {
      etat.setPin('4821');

      service.completeOnboarding().subscribe();
      const requete = httpMock.expectOne(`${BASE}/complete`);

      // Dernier appel du parcours, et celui qui transporte le secret le plus
      // sensible : il doit rester dans le corps.
      expect(requete.request.urlWithParams).toBe(`${BASE}/complete`);
      expect(requete.request.urlWithParams).not.toContain('4821');

      requete.flush({});
    });

    it('laisse remonter un dossier déjà finalisé', () => {
      let statut: number | undefined;
      let message: string | undefined;
      let succesAppele = false;

      etat.setLinkToken('jwt-du-lien');
      etat.setPin('4821');

      service.completeOnboarding().subscribe({
        next: () => (succesAppele = true),
        error: (erreur: HttpErrorResponse) => {
          statut = erreur.status;
          message = erreur.error?.message;
        },
      });

      httpMock
        .expectOne(`${BASE}/complete`)
        .flush({ message: 'Ce compte a déjà été activé.' }, { status: 409, statusText: 'Conflict' });

      // Afficher l'écran de succès sur un 409 laisserait croire à une
      // deuxième activation et masquerait un doublon côté core banking.
      expect(succesAppele).toBe(false);
      expect(statut).toBe(409);
      expect(message).toBe('Ce compte a déjà été activé.');
    });

    it('transmet le client créé pour l\'écran de succès', () => {
      const attendu = {
        success: true,
        message: 'Ouverture terminée',
        data: {
          customerId: 'CUS-000123',
          firstName: 'Jean',
          lastName: 'Nkeng',
          message: 'Votre compte est actif.',
        },
        timestamp: '2026-08-09T10:00:00Z',
      };
      let recu: unknown;

      service.completeOnboarding().subscribe((reponse) => (recu = reponse));
      httpMock.expectOne(`${BASE}/complete`).flush(attendu);

      expect(recu).toEqual(attendu);
    });
  });
});
