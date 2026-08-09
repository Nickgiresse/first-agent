import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { DocumentService } from './document';
import { OnboardingState } from './onboarding-state';
import { environment } from '../../../environments/environment';
import { OcrConfirmationRequest } from '../models/ocr.model';

/**
 * Toutes les pièces du dossier KYC passent par ce service : pièce d'identité,
 * selfie, extraction OCR et défi de vivacité.
 *
 * Deux familles de fautes y sont indétectables autrement. La première est
 * l'oubli de l'en-tête `X-Session-Token` sur une méthode : le backend rejette,
 * le client voit « service indisponible » et rien n'indique laquelle des neuf
 * routes est en cause. La seconde touche les envois multipart, dont le contenu
 * n'est pas typé : un champ mal nommé ou des images écrasées compilent
 * parfaitement et ne se voient qu'à la lecture des journaux du serveur.
 */
describe('DocumentService', () => {
  const BASE = `${environment.apiUrl}/documents`;
  const JETON = 'jeton-de-session';

  let service: DocumentService;
  let etat: OnboardingState;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DocumentService);
    etat = TestBed.inject(OnboardingState);
    httpMock = TestBed.inject(HttpTestingController);

    etat.setAccountVerified(JETON, 'Jean', 'Nkeng', 600);
  });

  afterEach(() => {
    // Aucune requête ne doit rester en suspens : un appel parti en double
    // relancerait une extraction OCR facturée deux fois.
    httpMock.verify();
    sessionStorage.clear();
  });

  function fichierCni(): File {
    return new File(['contenu binaire'], 'cni-recto.jpg', { type: 'image/jpeg' });
  }

  function image(): Blob {
    return new Blob(['image'], { type: 'image/jpeg' });
  }

  /**
   * Table des neuf routes du service.
   *
   * Elle est écrite à plat plutôt que déduite du code : c'est précisément
   * l'écart entre ce qui est attendu et ce qui est envoyé que l'on cherche à
   * voir. Une route déplacée côté backend doit faire échouer une ligne ici, pas
   * apparaître en production.
   */
  const routes: ReadonlyArray<{ nom: string; verbe: string; url: string; appeler: () => void }> = [
    {
      nom: 'uploadDocument',
      verbe: 'POST',
      url: `${BASE}/upload?documentType=CNI_RECTO`,
      appeler: () => service.uploadDocument('CNI_RECTO', fichierCni()).subscribe(),
    },
    {
      nom: 'extractOcrData',
      verbe: 'POST',
      url: `${BASE}/ocr/extract`,
      appeler: () => service.extractOcrData().subscribe(),
    },
    {
      nom: 'getOcrData',
      verbe: 'GET',
      url: `${BASE}/ocr`,
      appeler: () => service.getOcrData().subscribe(),
    },
    {
      nom: 'confirmOcrData',
      verbe: 'PUT',
      url: `${BASE}/ocr`,
      appeler: () => service.confirmOcrData({ firstName: 'Jean', lastName: 'Nkeng' }).subscribe(),
    },
    {
      nom: 'verifyFace',
      verbe: 'POST',
      url: `${BASE}/face-verification/verify`,
      appeler: () => service.verifyFace().subscribe(),
    },
    {
      nom: 'getFaceVerification',
      verbe: 'GET',
      url: `${BASE}/face-verification`,
      appeler: () => service.getFaceVerification().subscribe(),
    },
    {
      nom: 'startLivenessChallenge',
      verbe: 'POST',
      url: `${BASE}/liveness/challenge/start`,
      appeler: () => service.startLivenessChallenge().subscribe(),
    },
    {
      nom: 'verifyLivenessAction',
      verbe: 'POST',
      url: `${BASE}/liveness/challenge/verify`,
      appeler: () => service.verifyLivenessAction('BLINK', [image()]).subscribe(),
    },
    {
      nom: 'getLivenessStatus',
      verbe: 'GET',
      url: `${BASE}/liveness/challenge/status`,
      appeler: () => service.getLivenessStatus().subscribe(),
    },
  ];

  describe('adressage et authentification', () => {
    routes.forEach(({ nom, verbe, url, appeler }) => {
      it(`${nom} appelle ${verbe} sur sa route, en présentant le jeton de session`, () => {
        appeler();

        const requete = httpMock.expectOne(url);

        expect(requete.request.method).toBe(verbe);
        // L'en-tête est la seule preuve d'identité du parcours : sans lui, la
        // route répond 401 et l'écran affiche un échec technique sans rapport
        // avec ce que le client vient de faire.
        expect(requete.request.headers.get('X-Session-Token')).toBe(JETON);

        requete.flush({ success: true, message: '', data: null, timestamp: '' });
      });
    });
  });

  describe('jeton de session', () => {
    it('est relu à chaque appel et non figé à la construction du service', () => {
      service.getOcrData().subscribe();
      httpMock.expectOne(`${BASE}/ocr`).flush({});

      // Le jeton change quand la vérification de compte est rejouée. Un en-tête
      // construit une fois pour toutes enverrait l'ancien, expiré, et
      // bloquerait le dossier sans message exploitable.
      etat.setAccountVerified('nouveau-jeton', 'Jean', 'Nkeng', 600);

      service.getOcrData().subscribe();
      const seconde = httpMock.expectOne(`${BASE}/ocr`);
      expect(seconde.request.headers.get('X-Session-Token')).toBe('nouveau-jeton');
      seconde.flush({});
    });

    it('part vide plutôt que absent quand aucune session n\'est ouverte', () => {
      etat.clear();

      service.getOcrData().subscribe();
      const requete = httpMock.expectOne(`${BASE}/ocr`);

      // COMPORTEMENT ACTUEL, DOCUMENTÉ : l'appel part quand même, avec un
      // en-tête vide, et c'est le serveur qui refuse. Le garde de route est
      // censé rendre ce cas impossible ; s'il tombait, l'appel produirait un
      // 401 et non une erreur locale immédiate. Ce cas fige la valeur envoyée
      // pour que le backend puisse s'y fier plutôt que de gérer aussi
      // l'absence d'en-tête.
      expect(requete.request.headers.get('X-Session-Token')).toBe('');

      requete.flush({});
    });
  });

  describe('uploadDocument', () => {
    it('place le type de document dans l\'adresse et le fichier dans le corps', () => {
      const fichier = fichierCni();

      service.uploadDocument('SELFIE', fichier).subscribe();

      const requete = httpMock.expectOne(`${BASE}/upload?documentType=SELFIE`);
      const corps = requete.request.body as FormData;
      const joint = corps.get('file') as File;

      expect(corps).toBeInstanceOf(FormData);
      // Le champ s'appelle `file` : le backend le lit par ce nom, et une faute
      // de frappe donne un envoi accepté par le navigateur puis rejeté avec un
      // message sur un paramètre manquant, sans indiquer lequel.
      expect(joint).not.toBeNull();
      expect(joint.name).toBe(fichier.name);
      expect(joint.type).toBe('image/jpeg');

      requete.flush({ success: true, message: '', data: null, timestamp: '' });
    });

    it('laisse le navigateur poser lui-même le type de contenu multipart', () => {
      service.uploadDocument('CNI_VERSO', fichierCni()).subscribe();

      const requete = httpMock.expectOne(`${BASE}/upload?documentType=CNI_VERSO`);

      // Fixer `Content-Type` à la main sur un envoi multipart supprime la
      // frontière générée par le navigateur : le serveur reçoit alors un corps
      // qu'il ne sait pas découper. Le symptôme est un fichier vide côté
      // banque, pas une erreur réseau.
      expect(requete.request.headers.has('Content-Type')).toBe(false);

      requete.flush({ success: true, message: '', data: null, timestamp: '' });
    });

    it('distingue les trois types de pièces attendus par le dossier', () => {
      service.uploadDocument('CNI_RECTO', fichierCni()).subscribe();
      httpMock.expectOne(`${BASE}/upload?documentType=CNI_RECTO`).flush({});

      service.uploadDocument('CNI_VERSO', fichierCni()).subscribe();
      httpMock.expectOne(`${BASE}/upload?documentType=CNI_VERSO`).flush({});

      service.uploadDocument('SELFIE', fichierCni()).subscribe();
      // Une pièce enregistrée sous le mauvais type fait échouer le croisement
      // OCR / reconnaissance faciale beaucoup plus loin dans le parcours, sans
      // rapport apparent avec l'envoi.
      httpMock.expectOne(`${BASE}/upload?documentType=SELFIE`).flush({});
    });

    it('laisse remonter un fichier refusé par le serveur', () => {
      let statut: number | undefined;
      let succesAppele = false;

      service.uploadDocument('CNI_RECTO', fichierCni()).subscribe({
        next: () => (succesAppele = true),
        error: (erreur: HttpErrorResponse) => (statut = erreur.status),
      });

      httpMock
        .expectOne(`${BASE}/upload?documentType=CNI_RECTO`)
        .flush({ message: 'Fichier trop volumineux' }, { status: 413, statusText: 'Payload Too Large' });

      // Sans remontée, l'écran passerait à l'étape suivante avec un dossier
      // incomplet.
      expect(succesAppele).toBe(false);
      expect(statut).toBe(413);
    });
  });

  describe('corps des appels sans donnée', () => {
    it('envoie un objet vide plutôt que rien sur les POST déclencheurs', () => {
      service.extractOcrData().subscribe();
      const extraction = httpMock.expectOne(`${BASE}/ocr/extract`);
      expect(extraction.request.body).toEqual({});
      extraction.flush({});

      service.verifyFace().subscribe();
      const verification = httpMock.expectOne(`${BASE}/face-verification/verify`);
      expect(verification.request.body).toEqual({});
      verification.flush({});

      service.startLivenessChallenge().subscribe();
      const defi = httpMock.expectOne(`${BASE}/liveness/challenge/start`);
      // Un POST sans corps part sans `Content-Length` ni type : certains
      // intermédiaires le rejettent avant d'atteindre l'application, ce qui
      // donne une panne visible uniquement en production derrière le proxy.
      expect(defi.request.body).toEqual({});
      defi.flush({});
    });
  });

  describe('confirmOcrData', () => {
    it('corrige les données lues par un PUT, distinct de la lecture', () => {
      const correction: OcrConfirmationRequest = {
        firstName: 'Jean',
        lastName: 'Nkeng',
        documentNumber: '123456789',
        birthDate: '1990-04-12',
        birthPlace: 'Douala',
      };

      service.confirmOcrData(correction).subscribe();

      const requete = httpMock.expectOne(`${BASE}/ocr`);

      // Lecture et confirmation partagent la même adresse : seul le verbe les
      // sépare. Un POST à la place du PUT toucherait une route inexistante, et
      // un GET écraserait silencieusement la correction du client.
      expect(requete.request.method).toBe('PUT');
      // La charge utile part telle quelle : les corrections saisies à l'écran
      // sont ce que la banque enregistrera comme identité déclarée.
      expect(requete.request.body).toEqual(correction);

      requete.flush({ success: true, message: '', data: null, timestamp: '' });
    });

    it('laisse remonter un refus de concordance avec le titulaire du compte', () => {
      let message: string | undefined;
      let succesAppele = false;

      service.confirmOcrData({ firstName: 'Paul', lastName: 'Mbarga' }).subscribe({
        next: () => (succesAppele = true),
        error: (erreur: HttpErrorResponse) => (message = erreur.error?.message),
      });

      httpMock
        .expectOne(`${BASE}/ocr`)
        .flush(
          { message: "L'identité ne correspond pas au titulaire du compte." },
          { status: 409, statusText: 'Conflict' },
        );

      // Ce refus est une décision de conformité : la traiter comme un succès
      // ferait entrer au KYC une identité que la banque a explicitement
      // rejetée.
      expect(succesAppele).toBe(false);
      expect(message).toBe("L'identité ne correspond pas au titulaire du compte.");
    });
  });

  describe('verifyLivenessAction', () => {
    it('joint toutes les images capturées, chacune sous un nom distinct', () => {
      const images = [image(), image(), image()];

      service.verifyLivenessAction('TURN_LEFT', images).subscribe();

      const requete = httpMock.expectOne(`${BASE}/liveness/challenge/verify`);
      const corps = requete.request.body as FormData;
      const jointes = corps.getAll('frames');

      // Le défi anti photo imprimée repose sur la séquence : n'envoyer que la
      // dernière image, ce que produirait un `set` au lieu d'un `append`,
      // rendrait le contrôle inopérant tout en continuant de répondre.
      expect(jointes).toHaveLength(3);
      expect((jointes[0] as File).name).toBe('frame0.jpg');
      expect((jointes[2] as File).name).toBe('frame2.jpg');

      requete.flush({ success: true, message: '', data: null, timestamp: '' });
    });

    it('nomme l\'action attendue dans le même envoi', () => {
      service.verifyLivenessAction('SMILE', [image()]).subscribe();

      const requete = httpMock.expectOne(`${BASE}/liveness/challenge/verify`);
      const corps = requete.request.body as FormData;

      // Le serveur vérifie que l'action réalisée est celle qu'il a tirée au
      // sort. Envoyer une action différente de celle demandée invaliderait le
      // défi pour un client qui a pourtant fait ce qu'on lui demandait.
      expect(corps.get('action')).toBe('SMILE');

      requete.flush({ success: true, message: '', data: null, timestamp: '' });
    });

    it('supporte une séquence vide sans partir en erreur locale', () => {
      // Cas d'une capture qui n'a produit aucune image (caméra coupée en cours
      // de défi) : l'appel doit atteindre le serveur, qui décide, plutôt que de
      // lever côté client et de perdre la trace de la tentative.
      service.verifyLivenessAction('BLINK', []).subscribe();

      const requete = httpMock.expectOne(`${BASE}/liveness/challenge/verify`);
      expect((requete.request.body as FormData).getAll('frames')).toHaveLength(0);

      requete.flush({ success: true, message: '', data: null, timestamp: '' });
    });
  });

  describe('lectures d\'état', () => {
    it('laisse remonter une vérification faciale encore absente', () => {
      let statut: number | undefined;

      service.getFaceVerification().subscribe({
        next: () => undefined,
        error: (erreur: HttpErrorResponse) => (statut = erreur.status),
      });

      httpMock
        .expectOne(`${BASE}/face-verification`)
        .flush({ message: 'Aucune vérification pour cette session' }, { status: 404, statusText: 'Not Found' });

      // L'écran distingue « pas encore fait » de « échoué » : sans le code de
      // statut, il ne peut pas.
      expect(statut).toBe(404);
    });

    it('transmet le statut du défi sans le réinterpréter', () => {
      const attendu = {
        success: true,
        message: '',
        data: {
          sessionId: 'session-1',
          actions: ['BLINK', 'SMILE'],
          completedActions: ['BLINK'],
          remainingActions: ['SMILE'],
          allActionsCompleted: false,
          live: false,
        },
        timestamp: '2026-08-09T10:00:00Z',
      };
      let recu: unknown;

      service.getLivenessStatus().subscribe((reponse) => (recu = reponse));
      httpMock.expectOne(`${BASE}/liveness/challenge/status`).flush(attendu);

      // `live` et `allActionsCompleted` viennent du serveur et de lui seul :
      // toute déduction côté client contournerait le contrôle de vivacité.
      expect(recu).toEqual(attendu);
    });
  });
});
