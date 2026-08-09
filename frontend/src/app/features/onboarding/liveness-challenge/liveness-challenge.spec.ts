import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Location } from '@angular/common';
import { WritableSignal, signal } from '@angular/core';
import { of, throwError } from 'rxjs';

import { LivenessChallenge } from './liveness-challenge';
import { CameraService } from '../../../core/services/camera';
import { DocumentService } from '../../../core/services/document';
import { NavigationService } from '../../../core/services/navigation';
import { LIVENESS_ACTION_LABELS, LivenessAction } from '../../../core/models/liveness.model';

/**
 * Preuve de vivacité : la seule étape du parcours qui distingue une personne
 * présente d'une photo imprimée brandie devant la caméra.
 *
 * Tout le reste du KYC peut être franchi avec une pièce d'identité volée et une
 * photo du titulaire. Ce qui l'empêche tient à trois choses, et à elles seules :
 * le serveur impose une suite d'actions qu'une photo ne peut pas exécuter, le
 * client doit les réussir une par une sans pouvoir en sauter, et le selfie
 * finalement envoyé à la comparaison faciale provient bien des images capturées
 * pendant ce défi.
 *
 * Les cas ci-dessous verrouillent ces trois points, ainsi que la libération de
 * la caméra, et documentent les endroits où le comportement actuel s'en écarte.
 */
describe('LivenessChallenge', () => {
  let start: ReturnType<typeof vi.fn>;
  let stop: ReturnType<typeof vi.fn>;
  let cameraError: WritableSignal<string | null>;
  let startLivenessChallenge: ReturnType<typeof vi.fn>;
  let verifyLivenessAction: ReturnType<typeof vi.fn>;
  let uploadDocument: ReturnType<typeof vi.fn>;
  let verifyFace: ReturnType<typeof vi.fn>;
  let navigateTo: ReturnType<typeof vi.fn>;
  let back: ReturnType<typeof vi.fn>;

  /** Cadence de la capture, telle que la fixe le composant. */
  const DELAI_AVANT_CAPTURE_MS = 800;
  const NOMBRE_IMAGES = 8;
  const INTERVALLE_IMAGES_MS = 200;
  const DUREE_CAPTURE_MS = DELAI_AVANT_CAPTURE_MS + NOMBRE_IMAGES * INTERVALLE_IMAGES_MS;

  const DEFI: LivenessAction[] = ['BLINK', 'SMILE'];
  const MESSAGE_PAR_DEFAUT = 'La vérification de vivacité a échoué.';

  /** Réponse du serveur à la vérification d'une action. */
  function reponseVerification(actionCompleted: boolean, allActionsCompleted = false) {
    return of({ data: { actionCompleted, allActionsCompleted } });
  }

  beforeEach(() => {
    // La capture s'étale sur 2,4 secondes de minuteurs enchaînés : sans horloge
    // simulée, chaque cas de ce fichier paierait ce délai en temps réel.
    vi.useFakeTimers();

    start = vi.fn().mockResolvedValue({ getTracks: () => [] });
    stop = vi.fn();
    cameraError = signal<string | null>(null);
    startLivenessChallenge = vi
      .fn()
      .mockReturnValue(of({ data: { sessionId: 'defi-1', actions: DEFI, expiresInSeconds: 90 } }));
    verifyLivenessAction = vi.fn().mockReturnValue(reponseVerification(true));
    uploadDocument = vi.fn().mockReturnValue(of({ data: {} }));
    verifyFace = vi.fn().mockReturnValue(of({ data: {} }));
    navigateTo = vi.fn();
    back = vi.fn();

    // jsdom ne sait ni lire un flux vidéo ni peindre un canvas : sans ces
    // doublures, aucune image ne serait produite et tous les cas tomberaient sur
    // le chemin d'échec au lieu de mesurer la règle visée.
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined);
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      drawImage: vi.fn(),
    } as unknown as CanvasRenderingContext2D);
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((callback: BlobCallback) => {
      callback(new Blob(['image'], { type: 'image/jpeg' }));
    });

    TestBed.configureTestingModule({
      imports: [LivenessChallenge],
      providers: [
        { provide: CameraService, useValue: { start, stop, error: cameraError, stream: signal(null) } },
        {
          provide: DocumentService,
          useValue: { startLivenessChallenge, verifyLivenessAction, uploadDocument, verifyFace },
        },
        { provide: NavigationService, useValue: { navigateTo } },
        { provide: Location, useValue: { back } },
      ],
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  /**
   * Ouvre l'écran et laisse la caméra démarrer puis le défi arriver.
   *
   * Le démarrage passe par `afterNextRender` puis par deux promesses : rendre la
   * vue ne suffit pas, il faut aussi laisser tourner la file des micro-tâches.
   */
  async function ouvrirLEcran(): Promise<ComponentFixture<LivenessChallenge>> {
    const fixture = TestBed.createComponent(LivenessChallenge);
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(1);
    return fixture;
  }

  /** Laisse se dérouler la salve d'images d'une action, puis sa vérification. */
  async function laisserLaCaptureSeDerouler(): Promise<void> {
    await vi.advanceTimersByTimeAsync(DUREE_CAPTURE_MS);
  }

  describe('démarrage', () => {
    it('demarrage_ouvreLaCameraFrontaleEtNonLaCameraArriere', async () => {
      // La caméra arrière filmerait ce que le client a devant lui : aucune des
      // actions demandées ne serait détectable, et l'écran deviendrait un mur.
      await ouvrirLEcran();

      expect(start).toHaveBeenCalledWith('user');
    });

    it('demarrage_defiRecu_afficheLaPremiereActionEtRienDePlus', async () => {
      const fixture = await ouvrirLEcran();

      expect(fixture.componentInstance.actions()).toEqual(DEFI);
      expect(fixture.componentInstance.currentActionIndex()).toBe(0);
      expect(fixture.componentInstance.currentAction()).toBe('BLINK');
      expect(fixture.componentInstance.phase()).toBe('READY');
    });

    it("demarrage_defiRecu_afficheLInstructionDeLActionEnCours", async () => {
      // Le client exécute ce qu'il lit. Une instruction qui ne correspond pas à
      // l'action envoyée au serveur ferait échouer un client parfaitement
      // coopératif, sans qu'il puisse comprendre pourquoi.
      const fixture = await ouvrirLEcran();
      fixture.detectChanges();

      const instruction: HTMLElement | null = fixture.nativeElement.querySelector('.liveness-instruction');

      expect(instruction?.textContent?.trim()).toBe(LIVENESS_ACTION_LABELS['BLINK']);
    });

    it('demarrage_cameraRefusee_neConsommeAucunDefi', async () => {
      // Un défi a une durée de validité côté serveur. L'ouvrir alors qu'aucune
      // image ne pourra être prise le laisserait expirer pour rien, et le client
      // repasserait par un second défi au lieu de régler son autorisation.
      start.mockResolvedValue(null);

      const fixture = await ouvrirLEcran();

      expect(startLivenessChallenge).not.toHaveBeenCalled();
      // La phase d'erreur, et non « LOADING » : elle porte un message et un
      // bouton, là où l'écran restait autrement figé sur « Préparation ».
      expect(fixture.componentInstance.phase()).toBe('ERROR');
    });

    it("demarrage_cameraRefusee_offreUneReprise", async () => {
      // `init()` sortait en silence quand la caméra n'avait pas démarré : la
      // phase restait `LOADING`, l'écran affichait « Préparation de la caméra… »
      // indéfiniment, et aucun bouton ne permettait de relancer. Le client qui
      // venait d'accorder l'autorisation dans la barre du navigateur n'avait
      // d'autre choix que de recharger la page, ce que rien ne lui indiquait.
      start.mockResolvedValue(null);
      cameraError.set('Impossible d’accéder à la caméra.');

      const fixture = await ouvrirLEcran();
      fixture.detectChanges();

      expect(fixture.componentInstance.phase()).toBe('ERROR');
      expect(fixture.componentInstance.error()).toContain('caméra');
      expect(fixture.nativeElement.querySelector('.primary-button')).not.toBeNull();
    });

    it('demarrage_defiIndisponible_afficheLeMessageMetier', async () => {
      startLivenessChallenge.mockReturnValue(
        throwError(() => ({ error: { message: 'Session expirée, reprenez le parcours.' } })),
      );

      const fixture = await ouvrirLEcran();

      expect(fixture.componentInstance.error()).toBe('Session expirée, reprenez le parcours.');
      // ERROR et non RETRY : aucune action n'ayant été reçue, il n'y a rien à
      // « réessayer » action par action. C'est toute la vérification qui doit
      // être reprise.
      expect(fixture.componentInstance.phase()).toBe('ERROR');
    });

    it("demarrage_defiIndisponible_offreUneRepriseQuiRejoueLeDemarrage", async () => {
      // L'échec du démarrage retombait sur la même phase `RETRY` que l'échec
      // d'une action, alors que les deux situations n'ont rien de commun : ici
      // aucune action n'a été reçue. « Réessayer » repassait donc en `READY`,
      // puis « Commencer » basculait en `CAPTURING` et la capture ressortait
      // aussitôt faute d'action courante : l'écran restait figé sur « Capture
      // en cours », sans image, sans appel, et sans même le message d'erreur.
      startLivenessChallenge.mockReturnValue(throwError(() => ({ error: { message: 'Service indisponible' } })));
      const fixture = await ouvrirLEcran();

      expect(fixture.componentInstance.phase()).toBe('ERROR');

      // La reprise rejoue le démarrage, au lieu de proposer de « réessayer »
      // une action qui n'existe pas.
      startLivenessChallenge.mockReturnValue(of({ data: { actions: ['BLINK'] } }));
      fixture.componentInstance.restartChallenge();
      // Même avance que l'ouverture de l'écran : la suite tourne sur minuteurs
      // simulés, et `whenStable` n'y rendrait jamais la main.
      await vi.advanceTimersByTimeAsync(1);

      expect(startLivenessChallenge).toHaveBeenCalledTimes(2);
      expect(fixture.componentInstance.phase()).toBe('READY');
      expect(verifyLivenessAction).not.toHaveBeenCalled();
    });
  });

  describe('enchaînement des actions', () => {
    it('capture_envoieUneSalveDImagesEtNonUneSeule', async () => {
      // Une image unique ne prouve rien : c'est exactement ce qu'une photo
      // imprimée fournit. Seule une suite d'images prise pendant le mouvement
      // permet au serveur de constater que le mouvement a eu lieu.
      const fixture = await ouvrirLEcran();

      fixture.componentInstance.startCapture();
      await laisserLaCaptureSeDerouler();

      expect(verifyLivenessAction).toHaveBeenCalledTimes(1);
      expect(verifyLivenessAction.mock.calls[0][0]).toBe('BLINK');
      expect(verifyLivenessAction.mock.calls[0][1]).toHaveLength(NOMBRE_IMAGES);
    });

    it("capture_laisseAuClientLeTempsDeCommencerLeMouvement", async () => {
      // Les premières images sont prises avant que la personne ait bougé si la
      // capture démarre au clic. L'action serait déclarée non détectée alors
      // qu'elle a bien été faite, une fraction de seconde trop tard.
      const fixture = await ouvrirLEcran();

      fixture.componentInstance.startCapture();
      await vi.advanceTimersByTimeAsync(DELAI_AVANT_CAPTURE_MS - 1);

      expect(HTMLCanvasElement.prototype.toBlob).not.toHaveBeenCalled();
    });

    it('capture_actionValidee_passeALActionSuivante', async () => {
      const fixture = await ouvrirLEcran();

      fixture.componentInstance.startCapture();
      await laisserLaCaptureSeDerouler();

      expect(fixture.componentInstance.currentActionIndex()).toBe(1);
      expect(fixture.componentInstance.currentAction()).toBe('SMILE');
      expect(fixture.componentInstance.phase()).toBe('READY');
    });

    it('capture_defiComplet_verifieChaqueActionDansLOrdreRecu', async () => {
      // L'ordre est tiré par le serveur pour ce défi précis. Envoyer les images
      // sous une autre étiquette que l'action réellement demandée reviendrait à
      // valider n'importe quel mouvement.
      verifyLivenessAction
        .mockReturnValueOnce(reponseVerification(true))
        .mockReturnValueOnce(reponseVerification(true, true));
      const fixture = await ouvrirLEcran();

      fixture.componentInstance.startCapture();
      await laisserLaCaptureSeDerouler();
      fixture.componentInstance.startCapture();
      await laisserLaCaptureSeDerouler();

      expect(verifyLivenessAction.mock.calls.map(appel => appel[0])).toEqual(DEFI);
    });

    it('capture_actionNonDetectee_neFaitPasAvancerLEtape', async () => {
      // Le cœur de la protection. Si un refus faisait tout de même progresser
      // l'index, il suffirait d'enchaîner les clics sans rien faire devant la
      // caméra pour arriver au bout du défi.
      verifyLivenessAction.mockReturnValue(reponseVerification(false));
      const fixture = await ouvrirLEcran();

      fixture.componentInstance.startCapture();
      await laisserLaCaptureSeDerouler();

      expect(fixture.componentInstance.currentActionIndex()).toBe(0);
      expect(fixture.componentInstance.currentAction()).toBe('BLINK');
      expect(fixture.componentInstance.phase()).toBe('RETRY');
    });

    it('retryCurrentAction_rejoueLaMemeActionEtNonLaSuivante', async () => {
      verifyLivenessAction.mockReturnValue(reponseVerification(false));
      const fixture = await ouvrirLEcran();
      fixture.componentInstance.startCapture();
      await laisserLaCaptureSeDerouler();

      fixture.componentInstance.retryCurrentAction();
      fixture.componentInstance.startCapture();
      await laisserLaCaptureSeDerouler();

      expect(fixture.componentInstance.phase()).toBe('RETRY');
      expect(verifyLivenessAction.mock.calls.map(appel => appel[0])).toEqual(['BLINK', 'BLINK']);
    });

    it('retryCurrentAction_effaceLeMessageDeLaTentativePrecedente', async () => {
      // Le message porte sur la tentative qui vient d'échouer. Le laisser
      // affiché pendant la suivante ferait croire à un second refus.
      verifyLivenessAction.mockReturnValue(throwError(() => ({ error: { message: 'Visage non détecté' } })));
      const fixture = await ouvrirLEcran();
      fixture.componentInstance.startCapture();
      await laisserLaCaptureSeDerouler();

      fixture.componentInstance.retryCurrentAction();

      expect(fixture.componentInstance.error()).toBeNull();
      expect(fixture.componentInstance.phase()).toBe('READY');
    });

    it("capture_echecsRepetes_neSontJamaisBridesParLEcran", async () => {
      // DÉFAUT DE PRODUCTION, comportement documenté tel qu'il est aujourd'hui.
      //
      // Rien ne compte les échecs. Un attaquant qui présente une photo peut
      // rejouer la même action indéfiniment, et tenter sa chance sur chaque
      // variation d'éclairage ou d'angle jusqu'à ce que le détecteur cède. La
      // seule limite est l'expiration du défi côté serveur.
      //
      // CE QUI DEVRAIT ÊTRE : au bout de quelques refus consécutifs, l'écran
      // doit fermer le défi et renvoyer le dossier en revue manuelle, comme le
      // fait déjà l'écran de code PIN après plusieurs codes erronés.
      verifyLivenessAction.mockReturnValue(reponseVerification(false));
      const fixture = await ouvrirLEcran();

      for (let essai = 0; essai < 10; essai++) {
        fixture.componentInstance.retryCurrentAction();
        fixture.componentInstance.startCapture();
        await laisserLaCaptureSeDerouler();
      }

      expect(verifyLivenessAction).toHaveBeenCalledTimes(10);
      expect(fixture.componentInstance.currentActionIndex()).toBe(0);
    });

    it('startCapture_doubleClic_neSauteAucuneAction', async () => {
      // Sans verrou, deux clics rapprochés lançaient deux salves en parallèle
      // sur la MÊME action, et chaque réponse faisait avancer l'index d'un
      // cran. L'action intermédiaire n'était jamais demandée, et le défi se
      // terminait avec une épreuve de moins que ce que le serveur avait tiré :
      // c'était le contournement le plus direct de la protection contre la
      // photo imprimée.
      //
      // Le gabarit masque bien le bouton dès la phase `CAPTURING`, mais
      // seulement à la détection de changement suivante : sur un appareil lent,
      // ou sur un double appui, les deux événements partent avant.
      const fixture = await ouvrirLEcran();

      fixture.componentInstance.startCapture();
      fixture.componentInstance.startCapture();
      await laisserLaCaptureSeDerouler();

      expect(verifyLivenessAction.mock.calls.map(appel => appel[0])).toEqual(['BLINK']);
      expect(fixture.componentInstance.currentActionIndex()).toBe(1);
      expect(fixture.componentInstance.currentAction()).toBe('SMILE');
    });

    it("startCapture_sansAucuneActionRecue_neFigePasLEcran", async () => {
      // La phase `CAPTURING` était posée AVANT de vérifier l'action courante :
      // en son absence, l'écran affichait « Capture en cours » pour toujours,
      // sans bouton, sans message et sans le moindre appel réseau.
      const fixture = await ouvrirLEcran();
      fixture.componentInstance.actions.set([]);

      fixture.componentInstance.startCapture();
      await laisserLaCaptureSeDerouler();

      expect(verifyLivenessAction).not.toHaveBeenCalled();
      // L'écran reste sur l'état d'où le client peut agir.
      expect(fixture.componentInstance.phase()).toBe('READY');
    });

    it("capture_aucuneImageObtenue_nInterrogePasLeServeur", async () => {
      // Une vérification sans image consomme une tentative côté serveur et
      // revient forcément négative : le client serait accusé de ne pas avoir
      // bougé alors que c'est la capture qui a échoué.
      vi.mocked(HTMLCanvasElement.prototype.toBlob).mockImplementation((callback: BlobCallback) => {
        callback(null);
      });
      const fixture = await ouvrirLEcran();

      fixture.componentInstance.startCapture();
      await laisserLaCaptureSeDerouler();

      expect(verifyLivenessAction).not.toHaveBeenCalled();
      expect(fixture.componentInstance.phase()).toBe('RETRY');
      expect(fixture.componentInstance.error()).toBe(MESSAGE_PAR_DEFAUT);
    });
  });

  describe('échecs de vérification', () => {
    it('verification_erreurReseau_rendLaMainSurLaMemeAction', async () => {
      verifyLivenessAction.mockReturnValue(throwError(() => ({ error: { message: 'Service de vivacité arrêté' } })));
      const fixture = await ouvrirLEcran();

      fixture.componentInstance.startCapture();
      await laisserLaCaptureSeDerouler();

      expect(fixture.componentInstance.error()).toBe('Service de vivacité arrêté');
      expect(fixture.componentInstance.phase()).toBe('RETRY');
      // Une panne réseau ne prouve rien sur le client : elle ne doit pas lui
      // faire perdre l'action déjà réussie ni le faire progresser d'un cran.
      expect(fixture.componentInstance.currentActionIndex()).toBe(0);
    });

    it("verification_erreurHttp_afficheLeMessageMetierEtNonLeLibelleTechnique", async () => {
      // Une `HttpErrorResponse` porte toujours un `message` fabriqué par
      // Angular, en anglais et truffé d'URL. Lu avant `error.message`, il
      // masquerait la seule phrase que le client peut suivre.
      verifyLivenessAction.mockReturnValue(
        throwError(() => ({
          status: 422,
          message:
            'Http failure response for /api/documents/liveness/challenge/verify: 422 Unprocessable Entity',
          error: { message: 'Votre visage n’est pas assez visible, rapprochez-vous de la caméra.' },
        })),
      );
      const fixture = await ouvrirLEcran();

      fixture.componentInstance.startCapture();
      await laisserLaCaptureSeDerouler();

      expect(fixture.componentInstance.error()).toBe(
        'Votre visage n’est pas assez visible, rapprochez-vous de la caméra.',
      );
    });

    it('verification_erreurSansMessage_afficheLeLibelleParDefaut', async () => {
      // Sans repli, le cadre d'erreur s'afficherait vide et le client resterait
      // devant un bouton « Réessayer » sans savoir ce qui a échoué.
      verifyLivenessAction.mockReturnValue(throwError(() => ({})));
      const fixture = await ouvrirLEcran();

      fixture.componentInstance.startCapture();
      await laisserLaCaptureSeDerouler();

      expect(fixture.componentInstance.error()).toBe(MESSAGE_PAR_DEFAUT);
    });
  });

  describe('finalisation', () => {
    /** Amène l'écran au bout du défi en une seule action validée. */
    async function terminerLeDefi(fixture: ComponentFixture<LivenessChallenge>): Promise<void> {
      verifyLivenessAction.mockReturnValue(reponseVerification(true, true));
      fixture.componentInstance.startCapture();
      await laisserLaCaptureSeDerouler();
    }

    it('finalisation_envoieUnSelfieIssuDuDefiQuiVientDEtreValide', async () => {
      // Le selfie part ensuite en comparaison faciale contre la pièce
      // d'identité. S'il pouvait venir d'ailleurs que des images vérifiées, la
      // preuve de vivacité et la comparaison porteraient sur deux visages
      // différents, et tout le contrôle s'effondrerait.
      const fixture = await ouvrirLEcran();

      await terminerLeDefi(fixture);

      expect(uploadDocument).toHaveBeenCalledTimes(1);
      const [type, fichier] = uploadDocument.mock.calls[0] as [string, File];
      expect(type).toBe('SELFIE');
      expect(fichier.name).toBe('selfie.jpg');
      expect(fichier.type).toBe('image/jpeg');
    });

    it('finalisation_retientLaPremiereImageCapturee', async () => {
      // La première image est prise juste après le signal de départ, quand le
      // visage est encore de face. Les dernières sont prises en plein mouvement
      // de tête et donnent une comparaison faciale beaucoup plus fragile.
      let rang = 0;
      vi.mocked(HTMLCanvasElement.prototype.toBlob).mockImplementation((callback: BlobCallback) => {
        rang += 1;
        callback(new Blob([`image-${rang}`], { type: 'image/jpeg' }));
      });
      const fixture = await ouvrirLEcran();

      await terminerLeDefi(fixture);

      const [, fichier] = uploadDocument.mock.calls[0] as [string, File];
      expect(await fichier.text()).toBe('image-1');
    });

    it('finalisation_libereLaCameraUneFoisLaComparaisonAboutie', async () => {
      // Le témoin lumineux de la caméra reste allumé tant que le flux n'est pas
      // coupé. Le laisser courir pendant l'envoi puis la comparaison faciale,
      // qui peuvent prendre plusieurs secondes, donne au client la très
      // désagréable impression d'être filmé sans raison.
      const fixture = await ouvrirLEcran();

      await terminerLeDefi(fixture);

      expect(stop).toHaveBeenCalled();
      // La caméra est libérée APRÈS la comparaison faciale et non avant les
      // appels réseau. Coupée trop tôt, « Réessayer » recapturait sur un flux
      // éteint : aucune image ne sortait et le client tournait en rond.
      expect(stop.mock.invocationCallOrder[0]).toBeGreaterThan(
        uploadDocument.mock.invocationCallOrder[0],
      );
    });

    it('finalisation_succes_conduitAuxConditionsGenerales', async () => {
      const fixture = await ouvrirLEcran();

      await terminerLeDefi(fixture);

      expect(verifyFace).toHaveBeenCalled();
      expect(navigateTo).toHaveBeenCalledWith('/onboarding/terms-conditions');
    });

    it('finalisation_selfieDejaTeleverse_poursuitLaVerificationDuVisage', async () => {
      // Reprise de parcours : le selfie de la tentative précédente est déjà en
      // base. Traiter ce refus comme une panne bloquerait définitivement un
      // client qui n'a rien fait de mal, sur un dossier par ailleurs complet.
      // Le statut 409 est le repère : la formulation, elle, ne l'est plus.
      uploadDocument.mockReturnValue(
        throwError(() => ({
          status: 409,
          error: { message: 'Selfie déjà téléversé pour cette session.' },
        })),
      );
      const fixture = await ouvrirLEcran();

      await terminerLeDefi(fixture);

      expect(verifyFace).toHaveBeenCalled();
      expect(navigateTo).toHaveBeenCalledWith('/onboarding/terms-conditions');
    });

    it("finalisation_doublonQuelleQueSoitLaTournure_poursuitLeParcours", async () => {
      // La reprise de parcours était reconnue en cherchant « déjà téléversé »
      // dans le message du serveur. La tournure française naturelle, « a déjà
      // été téléversé », intercale un mot et ne correspondait donc plus : le
      // même incident, annoncé autrement, bloquait le client. Aucun message du
      // backend ne contenait d'ailleurs plus cette suite de caractères.
      //
      // Le code de statut est le seul repère stable : 409 dit « existe déjà »,
      // quelle que soit la formulation et quelle que soit la langue.
      uploadDocument.mockReturnValue(
        throwError(() => ({
          status: 409,
          error: { message: 'Le selfie a déjà été téléversé pour cette session.' },
        })),
      );
      const fixture = await ouvrirLEcran();

      await terminerLeDefi(fixture);

      expect(verifyFace).toHaveBeenCalled();
      expect(navigateTo).toHaveBeenCalledWith('/onboarding/terms-conditions');
    });

    it("finalisation_envoiDuSelfieEnEchec_neFaitPasAvancerLeParcours", async () => {
      uploadDocument.mockReturnValue(throwError(() => ({ error: { message: 'Stockage indisponible' } })));
      const fixture = await ouvrirLEcran();

      await terminerLeDefi(fixture);

      expect(verifyFace).not.toHaveBeenCalled();
      expect(navigateTo).not.toHaveBeenCalled();
      expect(fixture.componentInstance.error()).toBe('Stockage indisponible');
    });

    it('finalisation_verificationDuVisageEnEchec_neFaitPasAvancerLeParcours', async () => {
      // Passer aux conditions générales sans que le visage ait été comparé à la
      // pièce d'identité reviendrait à ouvrir un compte sur la seule foi du
      // défi de vivacité, sans savoir à qui appartient ce visage.
      verifyFace.mockReturnValue(throwError(() => ({ error: { message: 'Visage non concordant' } })));
      const fixture = await ouvrirLEcran();

      await terminerLeDefi(fixture);

      expect(navigateTo).not.toHaveBeenCalled();
      expect(fixture.componentInstance.error()).toBe('Visage non concordant');
      expect(fixture.componentInstance.phase()).toBe('RETRY');
    });

    it("finalisation_enEchec_laisseLaCameraDisponiblePourUneReprise", async () => {
      // La caméra était coupée dès l'entrée en finalisation, avant l'envoi du
      // selfie et la comparaison faciale. Si l'un des deux échouait,
      // « Réessayer » recapturait sur un flux éteint : aucune image ne sortait,
      // et le client tournait en rond sans comprendre pourquoi.
      verifyFace.mockReturnValue(throwError(() => ({ error: { message: 'Comparaison indisponible' } })));
      const fixture = await ouvrirLEcran();
      await terminerLeDefi(fixture);

      // Le flux n'a jamais été arrêté : la reprise peut capturer.
      expect(stop).not.toHaveBeenCalled();
      expect(fixture.componentInstance.error()).toBe('Comparaison indisponible');
    });
  });

  describe('libération de la caméra', () => {
    it('destruction_libereLaCamera', async () => {
      // Sans cet arrêt, le flux reste ouvert après le départ de l'écran : le
      // témoin de la caméra ne s'éteint plus jusqu'à la fermeture de l'onglet,
      // sur une application bancaire qui vient justement de demander au client
      // de lui confier son visage.
      const fixture = await ouvrirLEcran();

      fixture.destroy();

      expect(stop).toHaveBeenCalledTimes(1);
    });

    it('destruction_avantMemeLeDemarrageDuDefi_libereLaCamera', async () => {
      // Le client qui referme l'écran pendant le chargement laisserait sinon une
      // caméra allumée derrière lui.
      const fixture = TestBed.createComponent(LivenessChallenge);
      fixture.detectChanges();
      await vi.advanceTimersByTimeAsync(1);

      fixture.destroy();

      expect(stop).toHaveBeenCalled();
    });

    it("destruction_pendantUneCapture_interromptLaSalve", async () => {
      // La boucle de capture dure près de deux secondes. Sans drapeau, un
      // client qui quittait l'écran pendant ce temps laissait la boucle tourner
      // sur un flux déjà arrêté, puis envoyer au serveur des images prises
      // après coup.
      const fixture = await ouvrirLEcran();
      fixture.componentInstance.startCapture();
      await vi.advanceTimersByTimeAsync(DELAI_AVANT_CAPTURE_MS + INTERVALLE_IMAGES_MS);

      fixture.destroy();
      await laisserLaCaptureSeDerouler();

      expect(stop).toHaveBeenCalled();
      expect(verifyLivenessAction).not.toHaveBeenCalled();
    });
  });

  describe('sortie de l’écran', () => {
    it('goBack_revientALEtapePrecedenteDuParcours', async () => {
      // Le retour passe par l'historique et non par une URL en dur : l'écran est
      // atteint depuis plusieurs points du parcours, et une cible figée
      // renverrait une partie des clients sur une étape déjà franchie.
      const fixture = await ouvrirLEcran();

      fixture.componentInstance.goBack();

      expect(back).toHaveBeenCalled();
    });
  });

  describe('libellés des actions', () => {
    it('actionLabels_couvreToutesLesActionsQueLeServeurPeutTirer', async () => {
      // Le serveur tire les actions dans une liste fermée. Une action sans
      // libellé afficherait une consigne vide : le client ne saurait pas quoi
      // faire, échouerait, et le dossier partirait en revue manuelle sans
      // qu'aucune erreur ne soit remontée.
      const fixture = await ouvrirLEcran();
      const toutesLesActions: LivenessAction[] = [
        'BLINK',
        'TURN_LEFT',
        'TURN_RIGHT',
        'SMILE',
        'LOOK_UP',
        'LOOK_DOWN',
      ];

      for (const action of toutesLesActions) {
        expect(fixture.componentInstance.actionLabels[action]).toBeTruthy();
      }
    });
  });
});
