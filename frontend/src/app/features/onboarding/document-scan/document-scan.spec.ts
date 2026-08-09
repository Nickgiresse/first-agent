import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Location } from '@angular/common';
import { signal } from '@angular/core';
import { NEVER, of, throwError } from 'rxjs';
import type { MockInstance } from 'vitest';

import { DocumentScan } from './document-scan';
import { CameraService } from '../../../core/services/camera';
import { DocumentService } from '../../../core/services/document';
import { NavigationService } from '../../../core/services/navigation';

/**
 * Capture de la pièce d'identité à la caméra.
 *
 * C'est le seul écran du parcours qui prend le contrôle d'un périphérique du
 * client. Deux familles de fautes s'y logent, et aucune ne ferait échouer un
 * test technique.
 *
 * La première tient au matériel : un flux vidéo qu'on oublie de fermer laisse
 * le voyant de la caméra allumé, empêche les autres applications de l'ouvrir et
 * continue de consommer la batterie. Le client, lui, croit avoir quitté l'écran.
 *
 * La seconde tient au parcours : deux faces à capturer dans l'ordre, une bascule
 * automatique de l'une à l'autre, et un échec de traitement qui doit rendre la
 * caméra au client au lieu de le laisser devant une image morte.
 */
describe('DocumentScan', () => {
  let component: DocumentScan;
  let start: ReturnType<typeof vi.fn>;
  let stop: ReturnType<typeof vi.fn>;
  let uploadDocument: ReturnType<typeof vi.fn>;
  let extractOcrData: ReturnType<typeof vi.fn>;
  let navigateTo: ReturnType<typeof vi.fn>;
  let back: ReturnType<typeof vi.fn>;

  let erreurCamera: ReturnType<typeof signal<string | null>>;
  let contexte: { drawImage: ReturnType<typeof vi.fn>; getImageData: ReturnType<typeof vi.fn> };
  let encoder: MockInstance<HTMLCanvasElement['toBlob']>;
  let imagesDemandees: FrameRequestCallback[];
  let imagesAnnulees: number[];

  const LARGEUR_ANALYSE = 200;
  const HAUTEUR_ANALYSE = 150;

  /** Réponse du service documentaire, dans la forme rendue par le backend. */
  function reponse(corps: Record<string, unknown> = {}) {
    return of({ success: true, message: '', data: corps, timestamp: '2026-08-09T10:00:00Z' });
  }

  /**
   * Image en damier 100/140 : nette (forte variance du laplacien), correctement
   * exposée, sans reflet, et contrastée sur les quatre bords du cadre. C'est
   * l'image que l'analyseur juge exploitable, donc celle qui déclenche la
   * capture automatique.
   */
  function imageExploitable(): ImageData {
    const pixels = new Uint8ClampedArray(LARGEUR_ANALYSE * HAUTEUR_ANALYSE * 4);
    for (let y = 0; y < HAUTEUR_ANALYSE; y++) {
      for (let x = 0; x < LARGEUR_ANALYSE; x++) {
        const decalage = (y * LARGEUR_ANALYSE + x) * 4;
        const valeur = (x + y) % 2 === 0 ? 100 : 140;
        pixels[decalage] = valeur;
        pixels[decalage + 1] = valeur;
        pixels[decalage + 2] = valeur;
        pixels[decalage + 3] = 255;
      }
    }
    return { data: pixels, width: LARGEUR_ANALYSE, height: HAUTEUR_ANALYSE } as unknown as ImageData;
  }

  /** Image uniforme et sombre : ni nette, ni cadrée, ni assez éclairée. */
  function imageInexploitable(): ImageData {
    const pixels = new Uint8ClampedArray(LARGEUR_ANALYSE * HAUTEUR_ANALYSE * 4);
    pixels.fill(20);
    for (let i = 3; i < pixels.length; i += 4) pixels[i] = 255;
    return { data: pixels, width: LARGEUR_ANALYSE, height: HAUTEUR_ANALYSE } as unknown as ImageData;
  }

  /** Flux vidéo tel que le service caméra le rend une fois l'autorisation donnée. */
  function flux(): MediaStream {
    return { getTracks: () => [{ stop: vi.fn() }] } as unknown as MediaStream;
  }

  /** Écran créé sans rendu : les crochets de rendu ne sont pas déclenchés. */
  function ecran(): DocumentScan {
    component = TestBed.createComponent(DocumentScan).componentInstance;
    return component;
  }

  /** Écran rendu et caméra ouverte, comme après l'arrivée du client sur la page. */
  async function ecranAvecCamera(): Promise<ComponentFixture<DocumentScan>> {
    const fixture = TestBed.createComponent(DocumentScan);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  /** Vidéo factice : seules ses dimensions comptent pour le recadrage. */
  function videoFactice(): HTMLVideoElement {
    return { videoWidth: 1280, videoHeight: 720 } as unknown as HTMLVideoElement;
  }

  /** Donne au flux rendu les caractéristiques d'une vidéo réellement décodée. */
  function preparerVideo(fixture: ComponentFixture<DocumentScan>): void {
    const video = fixture.componentInstance.videoRef()!.nativeElement;
    Object.defineProperty(video, 'readyState', { value: 4, configurable: true });
    Object.defineProperty(video, 'videoWidth', { value: 1280, configurable: true });
    Object.defineProperty(video, 'videoHeight', { value: 720, configurable: true });
  }

  /**
   * Identifiant rendu par le navigateur pour la dernière image demandée par la
   * boucle d'analyse du composant, ou `null` si elle n'a jamais été programmée.
   *
   * Le rendu d'Angular demande lui aussi des images : reconnaître la boucle à
   * sa fonction de rappel évite de prendre l'ordonnanceur du framework pour le
   * composant testé.
   */
  function identifiantDeLaBoucleDAnalyse(): number | null {
    const analyse = (component as unknown as { analyzeFrame: FrameRequestCallback }).analyzeFrame;
    const position = imagesDemandees.lastIndexOf(analyse);
    return position === -1 ? null : position + 1;
  }

  /** Rejoue une image de la boucle d'analyse à l'horodatage donné. */
  function analyser(horodatage: number): void {
    (component as unknown as { analyzeFrame: (t: number) => void }).analyzeFrame(horodatage);
  }

  /** Déclenche une capture, comme le fait la boucle d'analyse sur image stable. */
  function capturer(): void {
    (component as unknown as { capture: (v: HTMLVideoElement) => void }).capture(videoFactice());
  }

  beforeEach(() => {
    erreurCamera = signal<string | null>(null);
    start = vi.fn().mockResolvedValue(flux());
    stop = vi.fn();
    uploadDocument = vi.fn().mockReturnValue(reponse());
    extractOcrData = vi.fn().mockReturnValue(reponse());
    navigateTo = vi.fn();
    back = vi.fn();

    // Aucun de ces trois objets n'existe dans l'environnement de test : jsdom ne
    // sait ni décoder une vidéo, ni peindre un canvas, ni encoder une image. Les
    // remplacer ici est ce qui permet de faire tourner la vraie chaîne
    // analyse → capture → envoi sans dépendre d'un navigateur.
    contexte = { drawImage: vi.fn(), getImageData: vi.fn().mockReturnValue(imageExploitable()) };
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockImplementation(
      () => contexte as unknown as CanvasRenderingContext2D,
    );
    encoder = vi
      .spyOn(HTMLCanvasElement.prototype, 'toBlob')
      .mockImplementation((rappel: BlobCallback) => rappel(new Blob(['image'], { type: 'image/jpeg' })));
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined);

    imagesDemandees = [];
    imagesAnnulees = [];
    vi.spyOn(globalThis, 'requestAnimationFrame').mockImplementation(rappel => {
      imagesDemandees.push(rappel);
      return imagesDemandees.length;
    });
    vi.spyOn(globalThis, 'cancelAnimationFrame').mockImplementation(identifiant => {
      imagesAnnulees.push(identifiant);
    });

    TestBed.configureTestingModule({
      imports: [DocumentScan],
      providers: [
        { provide: CameraService, useValue: { start, stop, error: erreurCamera } },
        { provide: DocumentService, useValue: { uploadDocument, extractOcrData } },
        { provide: NavigationService, useValue: { navigateTo } },
        { provide: Location, useValue: { back } },
      ],
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("s'affiche sur la capture du recto", async () => {
    // Reprend ce que vérifiait le test généré à l'origine, en allant un pas
    // plus loin : le gabarit doit se rendre, l'écran doit démarrer sur le recto,
    // et la consigne affichée doit désigner cette face-là.
    const fixture = await ecranAvecCamera();

    expect(fixture.componentInstance).toBeTruthy();
    expect(fixture.componentInstance.step()).toBe('FRONT');
    expect(fixture.componentInstance.feedbackMessage()).toContain('recto');
    expect(fixture.componentInstance.error()).toBeNull();
  });

  describe('ouverture de la caméra', () => {
    it('arriveeSurLEcran_ouvreLaCameraEtLanceLAnalyse', async () => {
      // Sans démarrage automatique, le client attendrait devant un cadre noir
      // sans savoir qu'il doit agir : l'écran n'offre aucun bouton pour ouvrir
      // la caméra.
      await ecranAvecCamera();

      expect(start).toHaveBeenCalledTimes(1);
      expect(identifiantDeLaBoucleDAnalyse()).not.toBeNull();
    });

    it("autorisationRefusee_neLancePasLaBoucleDAnalyse", async () => {
      // Le client a refusé l'accès, ou aucune caméra n'est disponible. Lancer
      // la boucle malgré tout ferait tourner une analyse d'image à chaque
      // rafraîchissement d'écran sur une vidéo qui n'existe pas, pour toute la
      // durée de vie de l'onglet.
      // Le service caméra publie son message et ne rend aucun flux, comme
      // quand `getUserMedia` est refusé par le navigateur.
      erreurCamera.set('Impossible d’accéder à la caméra. Vérifiez les autorisations de votre navigateur.');
      start.mockResolvedValue(null);

      const fixture = await ecranAvecCamera();

      expect(identifiantDeLaBoucleDAnalyse()).toBeNull();
      expect(fixture.componentInstance.cameraError()).toContain('Impossible d’accéder à la caméra');
    });

    it("autorisationRefusee_laisseLaVoieDeReplisAccessible", async () => {
      // C'est la seule issue du client dont la caméra ne s'ouvre pas : le
      // bouton de dépôt manuel doit rester présent quoi qu'il arrive.
      start.mockResolvedValue(null);

      const fixture = await ecranAvecCamera();
      fixture.detectChanges();

      const repli: HTMLButtonElement | null = fixture.nativeElement.querySelector('.secondary-button');
      expect(repli).not.toBeNull();
    });
  });

  describe('libération de la caméra', () => {
    it('destruction_libereLeFluxVideo', async () => {
      // Le cas le plus visible pour le client : une caméra laissée ouverte
      // garde le voyant allumé après qu'il a quitté l'écran, empêche les
      // autres applications de l'utiliser et vide la batterie. Le flux doit
      // être fermé à la destruction, quelle que soit la façon dont l'écran a
      // été quitté : retour, navigation du parcours ou fermeture d'onglet.
      const fixture = await ecranAvecCamera();
      expect(start).toHaveBeenCalled();

      fixture.destroy();

      expect(stop).toHaveBeenCalledTimes(1);
    });

    it("destruction_arreteLaBoucleDAnalyse", async () => {
      // Une image en attente qui se rappelle après la destruction continue de
      // lire, de peindre et d'analyser une vidéo abandonnée, et d'écrire dans
      // les signaux d'un composant qui n'existe plus.
      const fixture = await ecranAvecCamera();
      const imageEnAttente = identifiantDeLaBoucleDAnalyse();

      fixture.destroy();

      expect(imageEnAttente).not.toBeNull();
      expect(imagesAnnulees).toContain(imageEnAttente);
    });

    it('destructionSansCameraOuverte_neCassePas', async () => {
      // Le client quitte l'écran avant que l'autorisation n'ait été donnée :
      // il n'y a alors aucune image en attente, et la fermeture ne doit pas
      // lever d'exception dans le crochet de destruction, ce qui empêcherait
      // Angular de détruire proprement le reste de l'écran.
      start.mockResolvedValue(null);
      const fixture = await ecranAvecCamera();

      expect(() => fixture.destroy()).not.toThrow();
      expect(identifiantDeLaBoucleDAnalyse()).toBeNull();
      expect(stop).toHaveBeenCalled();
    });

    it('useManualUpload_fermeLaCameraAvantDeQuitterLEcran', async () => {
      // Le dépôt manuel n'a aucun besoin de la caméra. La fermer seulement à la
      // destruction laisserait le voyant allumé pendant tout le temps que le
      // client passe à chercher ses photos dans sa galerie.
      await ecranAvecCamera();

      component.useManualUpload();

      expect(stop).toHaveBeenCalled();
      expect(navigateTo).toHaveBeenCalledWith('/onboarding/document-upload');
      expect(stop.mock.invocationCallOrder[0]).toBeLessThan(navigateTo.mock.invocationCallOrder[0]);
    });

    it("traitementDesImages_fermeLaCameraPendantLEnvoi", async () => {
      // Le téléversement et l'extraction OCR peuvent durer plusieurs dizaines
      // de secondes. Garder le flux ouvert pendant ce temps n'a aucune utilité
      // et fait chauffer le téléphone au moment précis où le client attend.
      await ecranAvecCamera();
      uploadDocument.mockReturnValue(NEVER);

      capturer();
      capturer();

      expect(component.step()).toBe('UPLOADING');
      expect(stop).toHaveBeenCalled();
    });
  });

  describe('consignes de cadrage', () => {
    beforeEach(() => ecran());

    /** Rejoue le libellé produit pour un rapport de qualité donné. */
    function consigne(rapport: Partial<Record<string, unknown>>): string {
      const complet = {
        sharpness: 30,
        brightness: 120,
        glareDetected: false,
        framed: true,
        overallOk: true,
        ...rapport,
      };
      return (component as unknown as { describe: (r: unknown) => string }).describe(complet);
    }

    it('cadrageManquant_designeLaFaceAttendue', () => {
      // La consigne doit nommer la face en cours, sans quoi le client retourne
      // sa pièce alors qu'on lui demandait encore le recto, et la capture
      // automatique ne se déclenche jamais.
      expect(consigne({ framed: false })).toContain('le recto');

      component.step.set('BACK');

      expect(consigne({ framed: false })).toContain('le verso');
    });

    it('refletDetecte_demandeDInclinerLeDocument', () => {
      // Un reflet efface une ligne entière de la pièce : l'OCR rendrait des
      // champs vides que le client devrait ressaisir à la main.
      expect(consigne({ glareDetected: true })).toContain('reflet');
    });

    it('lumiereInsuffisante_demandeDeSeRapprocherDUneLumiere', () => {
      expect(consigne({ brightness: 40 })).toContain('sombre');
    });

    it('lumiereExcessive_demandeDEviterLaLumiereDirecte', () => {
      expect(consigne({ brightness: 230 })).toContain('lumière');
    });

    it('imageFloue_demandeDeStabiliserLAppareil', () => {
      expect(consigne({ sharpness: 5 })).toContain('floue');
    });

    it('imageExploitable_annonceQueLaCaptureVaSeDeclencher', () => {
      // C'est le seul avertissement que reçoit le client avant le déclenchement
      // automatique : sans lui, la capture part alors qu'il bouge encore.
      expect(consigne({})).toBe('Parfait, ne bougez plus…');
    });

    it("cadrageManquant_primeSurLesAutresDefauts", () => {
      // Une pièce hors du cadre est aussi floue et mal éclairée. Annoncer la
      // netteté d'abord enverrait le client stabiliser son téléphone alors que
      // sa carte n'est simplement pas dans le cadre.
      expect(consigne({ framed: false, sharpness: 2, brightness: 10, glareDetected: true })).toContain(
        'entièrement dans le cadre',
      );
    });
  });

  describe('capture automatique', () => {
    it("imageInstable_neDeclencheAucuneCapture", async () => {
      // Capturer une image floue ou mal cadrée fait échouer l'extraction bien
      // plus loin, et le client doit alors tout recommencer.
      const fixture = await ecranAvecCamera();
      preparerVideo(fixture);
      contexte.getImageData.mockReturnValue(imageInexploitable());

      analyser(1_000);
      analyser(1_400);
      analyser(1_800);

      expect(encoder).not.toHaveBeenCalled();
      expect(component.step()).toBe('FRONT');
      // L'image a bien été jugée, et non simplement ignorée : la consigne
      // affichée est celle du défaut détecté.
      expect(component.feedbackMessage()).toContain('entièrement dans le cadre');
    });

    it('deuxImagesStables_neSuffisentPas', async () => {
      // Deux images de suite peuvent être stables par hasard, au moment où le
      // client approche sa pièce du cadre. Le seuil existe pour laisser le
      // temps à la main de s'immobiliser.
      const fixture = await ecranAvecCamera();
      preparerVideo(fixture);

      analyser(1_000);
      analyser(1_400);

      expect(encoder).not.toHaveBeenCalled();
    });

    it('troisImagesStables_declenchentLaCapture', async () => {
      const fixture = await ecranAvecCamera();
      preparerVideo(fixture);

      analyser(1_000);
      analyser(1_400);
      analyser(1_800);

      expect(encoder).toHaveBeenCalled();
      expect(component.step()).toBe('BACK');
    });

    it("uneImageInstableRemetLeCompteurAZero", async () => {
      // Sans remise à zéro, deux instants stables séparés par un mouvement
      // seraient additionnés, et la capture partirait pendant le mouvement.
      const fixture = await ecranAvecCamera();
      preparerVideo(fixture);

      analyser(1_000);
      analyser(1_400);
      contexte.getImageData.mockReturnValueOnce(imageInexploitable());
      analyser(1_800);
      analyser(2_200);
      analyser(2_600);

      expect(encoder).not.toHaveBeenCalled();
    });

    it("imagesTropRapprochees_neSontComptéesQuUneFois", async () => {
      // La boucle est appelée à chaque rafraîchissement d'écran, soit soixante
      // fois par seconde. Sans l'intervalle minimal, trois images consécutives
      // suffiraient à déclencher la capture en cinquante millisecondes, et
      // l'analyse complète de l'image tournerait soixante fois par seconde sur
      // le processeur du téléphone.
      const fixture = await ecranAvecCamera();
      preparerVideo(fixture);

      analyser(1_000);
      analyser(1_100);
      analyser(1_200);
      analyser(1_300);

      expect(contexte.getImageData).toHaveBeenCalledTimes(1);
      expect(encoder).not.toHaveBeenCalled();
    });

    it("videoNonDecodee_nEstPasAnalysee", async () => {
      // Au tout premier instant, la vidéo n'a pas encore de trame. Analyser un
      // canvas vide rendrait une image parfaitement uniforme, donc jugée
      // instable, mais surtout ferait tourner l'analyse pour rien.
      const fixture = await ecranAvecCamera();
      preparerVideo(fixture);
      Object.defineProperty(fixture.componentInstance.videoRef()!.nativeElement, 'readyState', {
        value: 1,
        configurable: true,
      });

      analyser(1_000);

      expect(contexte.getImageData).not.toHaveBeenCalled();
      expect(component.feedbackMessage()).toBe('Positionnez le recto de votre CNI dans le cadre.');
    });

    it("apresLaDestruction_lAnalyseNeReprendPas", async () => {
      // Une image déjà demandée peut encore se rappeler après la destruction :
      // la garde doit tenir, sinon le composant détruit continue d'analyser.
      const fixture = await ecranAvecCamera();
      preparerVideo(fixture);
      fixture.destroy();

      analyser(1_000);

      expect(contexte.getImageData).not.toHaveBeenCalled();
    });
  });

  describe('passage du recto au verso', () => {
    beforeEach(() => ecran());

    it('captureDuRecto_basculeSurLeVersoSansRienEnvoyer', () => {
      // Envoyer le recto dès sa capture laisserait un dossier à moitié
      // constitué côté banque si le client abandonne avant le verso.
      capturer();

      expect(component.step()).toBe('BACK');
      expect(component.feedbackMessage()).toBe('Recto capturé. Retournez la CNI et présentez le verso.');
      expect(uploadDocument).not.toHaveBeenCalled();
    });

    it('captureDuVerso_envoieLesDeuxFacesDansLeBonOrdre', () => {
      // Le type documentaire distingue les deux faces côté banque : les
      // inverser fait chercher le numéro de la pièce sur la photo du porteur.
      capturer();

      capturer();

      expect(uploadDocument).toHaveBeenNthCalledWith(1, 'CNI_RECTO', expect.anything());
      expect(uploadDocument).toHaveBeenNthCalledWith(2, 'CNI_VERSO', expect.anything());
      expect(uploadDocument.mock.calls[0][1].name).toBe('cni-recto.jpg');
      expect(uploadDocument.mock.calls[1][1].name).toBe('cni-verso.jpg');
    });

    it("captureDesDeuxFaces_enchaineSurLExtractionPuisLaRelecture", () => {
      capturer();

      capturer();

      expect(extractOcrData).toHaveBeenCalled();
      expect(navigateTo).toHaveBeenCalledWith('/onboarding/document-ocr-review');
    });

    it("encodageImpossible_resteSurLaMemeFace", () => {
      // Le canvas peut échouer à encoder, notamment quand la page passe en
      // arrière-plan. Basculer sur le verso malgré tout enverrait un recto vide
      // à la banque ; rester sur la face en cours laisse la boucle réessayer.
      encoder.mockImplementation((rappel: BlobCallback) => rappel(null));

      capturer();

      expect(component.step()).toBe('FRONT');
      expect(uploadDocument).not.toHaveBeenCalled();
    });

    it("goBack_depuisLEcran_revientALEtapePrecedente", () => {
      // Passe par l'historique et non par une route figée. La caméra n'est pas
      // fermée ici : c'est la destruction de l'écran par le routeur qui s'en
      // charge, ce que verrouille le cas « destruction_libereLeFluxVideo ».
      component.goBack();

      expect(back).toHaveBeenCalled();
    });
  });

  describe('échec du traitement', () => {
    beforeEach(() => ecran());

    /** Capture les deux faces, ce qui déclenche l'envoi. */
    function capturerLesDeuxFaces(): void {
      capturer();
      capturer();
    }

    it('echecDeLEnvoi_ramèneAuRectoEtRouvreLaCamera', () => {
      // Sans réouverture, le client se retrouve devant un cadre figé, avec un
      // message d'erreur et aucun moyen de recommencer : l'écran n'a pas de
      // bouton pour relancer la caméra.
      uploadDocument.mockReturnValue(throwError(() => ({ error: { message: 'Document illisible' } })));

      capturerLesDeuxFaces();

      expect(component.error()).toBe('Document illisible');
      expect(component.step()).toBe('FRONT');
      expect(start).toHaveBeenCalled();
    });

    it("echecDeLExtraction_ramèneAuRecto", () => {
      // Les deux faces sont bien arrivées, seule l'extraction a échoué : le
      // client doit pouvoir refaire ses deux prises sans quitter l'écran.
      extractOcrData.mockReturnValue(throwError(() => ({ error: { message: 'Moteur OCR indisponible' } })));

      capturerLesDeuxFaces();

      expect(component.error()).toBe('Moteur OCR indisponible');
      expect(component.step()).toBe('FRONT');
      expect(navigateTo).not.toHaveBeenCalled();
    });

    it("echec_oublieLesDeuxPrisesPrecedentes", () => {
      // Garder le recto de la tentative ratée le ferait renvoyer avec le verso
      // de la suivante : deux faces prises dans des conditions différentes, et
      // un dossier que le back-office ne peut pas rapprocher.
      uploadDocument.mockReturnValue(throwError(() => ({ error: { message: 'Panne' } })));
      capturerLesDeuxFaces();
      uploadDocument.mockReturnValue(reponse());

      capturer();

      // La prise suivante est bien comptée comme un recto, et non comme le
      // verso d'un dossier resté en mémoire.
      expect(component.step()).toBe('BACK');
      expect(extractOcrData).not.toHaveBeenCalled();
    });

    it('echecPuisNouvelleTentative_aboutit', () => {
      uploadDocument.mockReturnValue(throwError(() => ({ error: { message: 'Panne passagère' } })));
      capturerLesDeuxFaces();
      uploadDocument.mockReturnValue(reponse());

      capturerLesDeuxFaces();

      expect(navigateTo).toHaveBeenCalledWith('/onboarding/document-ocr-review');
    });

    it('erreurSansAucunMessage_afficheUnLibelleParDefaut', () => {
      uploadDocument.mockReturnValue(throwError(() => ({})));

      capturerLesDeuxFaces();

      expect(component.error()).toBe('Le traitement du document a échoué.');
    });

    it("erreurHttp_afficheLeMessageMetierEtNonLeLibelleTechnique", () => {
      // Le piège récurrent du projet : une HttpErrorResponse porte toujours un
      // `message` fabriqué par Angular, qui gagne s'il est lu avant
      // `err.error.message`. Le client lirait une phrase technique en anglais
      // au lieu de la seule consigne utile, à savoir ce qu'il doit changer à sa
      // prise de vue.
      uploadDocument.mockReturnValue(
        throwError(() => ({
          status: 422,
          message: 'Http failure response for /api/documents/upload: 422 Unprocessable Entity',
          error: { message: 'Le verso est illisible, refaites la prise sans reflet.' },
        })),
      );

      capturerLesDeuxFaces();

      expect(component.error()).toBe('Le verso est illisible, refaites la prise sans reflet.');
    });

    it("echec_retablitLaConsigneDeCapture", () => {
      // Le traitement a échoué et l'écran revient au recto ; la consigne
      // affichait pourtant encore « Analyse des documents en cours… ». Le
      // client voyait une erreur, un cadre de capture, et un message qui lui
      // disait d'attendre. Le libellé n'était corrigé qu'à la première image
      // analysée, donc jamais si la caméra ne se rouvrait pas.
      uploadDocument.mockReturnValue(throwError(() => ({ error: { message: 'Panne' } })));

      capturerLesDeuxFaces();

      expect(component.feedbackMessage()).toContain('recto');
      expect(component.feedbackMessage()).not.toContain('Analyse');
    });

    it("succes_retablitLEcranAvantDeNaviguer", () => {
      // Tant que la navigation aboutit, l'écran disparaît et une étape restée
      // sur « UPLOADING » ne se voit pas. Mais `NavigationService` avale les
      // échecs : un garde qui refuse laissait le client devant « Analyse des
      // documents en cours… », caméra fermée, sans cadre de capture, sans
      // message d'erreur et sans aucune action possible.
      capturerLesDeuxFaces();

      expect(component.step()).toBe('FRONT');
      expect(component.feedbackMessage()).toContain('recto');
    });

    it("priseIncomplete_nEnvoieRienEtInviteARefaireLaPrise", () => {
      // La garde est utile : sans elle, un fichier vide partirait à la banque.
      // C'était le message qui était faux. Il annonçait une session expirée et
      // renvoyait le client à la vérification de son compte, alors que sa
      // session est intacte et que seule une prise de vue s'est perdue : il
      // refaisait toute l'étape précédente pour rien.
      (component as unknown as { upload: () => void }).upload();

      expect(uploadDocument).not.toHaveBeenCalled();
      expect(component.error()).toContain('prises de vue');
      expect(component.error()).not.toContain('session');
      expect(component.step()).toBe('FRONT');
    });
  });
});
