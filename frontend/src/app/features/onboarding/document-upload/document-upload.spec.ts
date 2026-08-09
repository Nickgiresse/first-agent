import { TestBed } from '@angular/core/testing';
import { Location } from '@angular/common';
import { NEVER, of, throwError } from 'rxjs';

import { DocumentUpload } from './document-upload';
import { DocumentService } from '../../../core/services/document';
import { NavigationService } from '../../../core/services/navigation';

/**
 * Voie de repli du scan : le client dépose lui-même les deux faces de sa pièce
 * d'identité. C'est le seul écran du parcours où le fichier envoyé à la banque
 * est choisi par le client, et non fabriqué par l'application.
 *
 * Trois choses s'y jouent que rien d'autre ne protège : ce qui est accepté au
 * moment du choix, le fait que les deux faces partent bien dans le bon ordre et
 * sous le bon type, et le réarmement du bouton après un échec. Un client bloqué
 * ici n'a plus aucune voie de repli : le scan l'a déjà renvoyé vers cet écran.
 */
describe('DocumentUpload', () => {
  let component: DocumentUpload;
  let uploadDocument: ReturnType<typeof vi.fn>;
  let extractOcrData: ReturnType<typeof vi.fn>;
  let navigateTo: ReturnType<typeof vi.fn>;
  let back: ReturnType<typeof vi.fn>;

  const CINQ_MO = 5 * 1024 * 1024;

  /** Réponse du service documentaire, dans la forme rendue par le backend. */
  function reponse(corps: Record<string, unknown> = {}) {
    return of({ success: true, message: '', data: corps, timestamp: '2026-08-09T10:00:00Z' });
  }

  /**
   * Fabrique un fichier dont la taille est déclarée sans être réellement
   * allouée : un cas à 12 Mo ne doit pas coûter 12 Mo de mémoire au lanceur.
   */
  function fichier(nom: string, type = 'image/jpeg', taille = 1024): File {
    const resultat = new File(['x'], nom, { type });
    Object.defineProperty(resultat, 'size', { value: taille });
    return resultat;
  }

  /** Rejoue un choix de fichier tel que le champ `<input type="file">` l'émet. */
  function choisir(cote: 'front' | 'back', ...fichiers: File[]): void {
    const champ = document.createElement('input');
    Object.defineProperty(champ, 'files', { value: fichiers });
    const evenement = new Event('change');
    Object.defineProperty(evenement, 'target', { value: champ });

    component.pick(evenement, cote);
  }

  /** Amène l'écran à l'état où les deux faces sont choisies. */
  function choisirLesDeuxFaces(): void {
    choisir('front', fichier('recto.jpg'));
    choisir('back', fichier('verso.jpg'));
  }

  beforeEach(() => {
    uploadDocument = vi.fn().mockReturnValue(reponse());
    extractOcrData = vi.fn().mockReturnValue(reponse());
    navigateTo = vi.fn();
    back = vi.fn();

    TestBed.configureTestingModule({
      imports: [DocumentUpload],
      providers: [
        { provide: DocumentService, useValue: { uploadDocument, extractOcrData } },
        { provide: NavigationService, useValue: { navigateTo } },
        { provide: Location, useValue: { back } },
      ],
    });

    component = TestBed.createComponent(DocumentUpload).componentInstance;
  });

  it("s'affiche avec le bouton de validation inactif", () => {
    // Reprend ce que vérifiait le test généré à l'origine, en allant un pas
    // plus loin : le gabarit doit se rendre, et l'envoi doit rester fermé tant
    // qu'aucune face n'a été déposée.
    const fixture = TestBed.createComponent(DocumentUpload);
    fixture.detectChanges();

    const continuer: HTMLButtonElement | null =
      fixture.nativeElement.querySelector('.primary-button');

    expect(fixture.componentInstance).toBeTruthy();
    expect(continuer).not.toBeNull();
    expect(continuer!.disabled).toBe(true);
  });

  describe('choix des fichiers', () => {
    it('pick_recto_retientLeFichierEtPrepareUnApercu', async () => {
      // L'aperçu est la seule occasion pour le client de voir qu'il s'est
      // trompé de photo avant que celle-ci ne parte à la banque.
      choisir('front', fichier('recto.jpg'));

      expect(component.front()?.name).toBe('recto.jpg');
      await vi.waitFor(() => expect(component.frontPreview()).not.toBeNull());
    });

    it('pick_verso_nEcrasePasLeRecto', () => {
      // Les deux dépôts partagent la même méthode : une confusion entre les
      // deux signaux enverrait deux fois la même face à la banque, et le
      // verso manquant ne serait découvert qu'au contrôle manuel du dossier.
      choisir('front', fichier('recto.jpg'));

      choisir('back', fichier('verso.jpg'));

      expect(component.front()?.name).toBe('recto.jpg');
      expect(component.back()?.name).toBe('verso.jpg');
    });

    it('pick_choixAnnule_oublieLeFichierEtSonApercu', async () => {
      // Le client rouvre le sélecteur puis annule : le champ rend une liste
      // vide. Garder l'aperçu précédent lui laisserait croire que sa pièce est
      // toujours déposée alors que le bouton, lui, redevient inactif.
      choisir('front', fichier('recto.jpg'));
      await vi.waitFor(() => expect(component.frontPreview()).not.toBeNull());

      choisir('front');

      expect(component.front()).toBeNull();
      expect(component.frontPreview()).toBeNull();
    });

    it('pick_secondChoix_remplaceLApercuDuPremier', async () => {
      // Un aperçu resté sur l'ancienne photo ferait déposer une pièce
      // différente de celle que le client croit avoir choisie.
      choisir('front', fichier('flou.jpg'));
      await vi.waitFor(() => expect(component.frontPreview()).not.toBeNull());
      const premierApercu = component.frontPreview();

      choisir('front', fichier('net.jpg', 'image/png'));

      expect(component.front()?.name).toBe('net.jpg');
      await vi.waitFor(() => expect(component.frontPreview()).not.toBe(premierApercu));
    });

    it('pick_fichierDeDouzeMo_estRefuseAvantTouteMontee', () => {
      // Le gabarit annonce « JPG ou PNG, 5 Mo maximum par fichier » et rien ne
      // le vérifiait. Sur une connexion mobile, le client payait plusieurs
      // minutes de téléversement pour se voir refuser en fin de course, et sa
      // seule voie de repli, cet écran, venait d'échouer.
      choisir('front', fichier('scan-haute-definition.jpg', 'image/jpeg', CINQ_MO + 1));

      expect(component.front()).toBeNull();
      // Le message donne le poids réel et la limite : « fichier invalide »
      // laisserait le client rechoisir la même photo.
      expect(component.error()).toContain('5 Mo');
    });

    it('pick_fichierPdf_estRefuseEtNommeLeFormatAttendu', () => {
      // L'attribut `accept` du gabarit n'est qu'un filtre d'affichage du
      // sélecteur : le glisser-déposer et l'option « Tous les fichiers » le
      // contournent. Un PDF ou un HEIC d'iPhone atteignait donc le moteur OCR,
      // qui ne sait pas le lire, et le client recevait une erreur d'extraction
      // sans comprendre que c'était le format de son fichier qui était en cause.
      choisir('front', fichier('carte-identite.pdf', 'application/pdf'));

      expect(component.front()).toBeNull();
      expect(component.error()).toContain('JPEG');
    });
  });

  describe('garde de soumission', () => {
    it('submit_aucuneFace_nEnvoieRien', () => {
      component.submit();

      expect(uploadDocument).not.toHaveBeenCalled();
    });

    it('submit_versoManquant_nEnvoieMemePasLeRecto', () => {
      // Un dossier avec un seul côté est inexploitable : le verso porte le
      // numéro et la date d'expiration. Mieux vaut ne rien envoyer que laisser
      // un dossier à moitié constitué côté banque.
      choisir('front', fichier('recto.jpg'));

      component.submit();

      expect(uploadDocument).not.toHaveBeenCalled();
    });

    it('submit_rectoManquant_nEnvoieRien', () => {
      choisir('back', fichier('verso.jpg'));

      component.submit();

      expect(uploadDocument).not.toHaveBeenCalled();
    });

    it('submit_deuxFaces_envoieRectoPuisVersoDansLeBonOrdre', () => {
      // Le type documentaire est ce qui distingue les deux faces côté banque :
      // les inverser fait chercher le numéro de la pièce dans la photo du
      // porteur, et l'extraction échoue sans que la cause soit visible.
      const recto = fichier('recto.jpg');
      const verso = fichier('verso.jpg');
      choisir('front', recto);
      choisir('back', verso);

      component.submit();

      expect(uploadDocument).toHaveBeenNthCalledWith(1, 'CNI_RECTO', recto);
      expect(uploadDocument).toHaveBeenNthCalledWith(2, 'CNI_VERSO', verso);
      expect(extractOcrData).toHaveBeenCalled();
      expect(navigateTo).toHaveBeenCalledWith('/onboarding/document-ocr-review');
    });

    it("submit_versoRefuse_nAppellePasLExtraction", () => {
      // L'extraction sur un dossier incomplet rend des champs vides que le
      // client devrait ressaisir à la main : autant s'arrêter ici.
      uploadDocument
        .mockReturnValueOnce(reponse())
        .mockReturnValueOnce(throwError(() => ({ error: { message: 'Verso illisible' } })));
      choisirLesDeuxFaces();

      component.submit();

      expect(extractOcrData).not.toHaveBeenCalled();
      expect(navigateTo).not.toHaveBeenCalled();
    });

    it('submit_doubleClic_nEnvoieQuUneFoisLesFichiers', () => {
      // Avant la garde, la seule protection était l'attribut `disabled` du
      // gabarit. Sur un téléphone lent, les deux clics passaient avant le
      // premier rendu et le dossier recevait quatre pièces au lieu de deux.
      //
      // Le premier envoi reste en vol : c'est exactement l'instant où le second
      // clic arrive.
      uploadDocument.mockReturnValue(NEVER);
      choisirLesDeuxFaces();

      component.submit();
      component.submit();

      expect(uploadDocument).toHaveBeenCalledTimes(1);
    });
  });

  describe('échec du traitement', () => {
    it('submit_echecDuRecto_reactiveLeBouton', () => {
      // Le cas qui bloque réellement les clients : si `sending` ne retombe pas,
      // le bouton reste inerte jusqu'au rechargement de la page, et cet écran
      // est déjà la voie de repli du scan.
      uploadDocument.mockReturnValue(throwError(() => ({ error: { message: 'Fichier rejeté' } })));
      choisirLesDeuxFaces();

      component.submit();

      expect(component.error()).toBe('Fichier rejeté');
      expect(component.sending()).toBe(false);
      expect(navigateTo).not.toHaveBeenCalled();
    });

    it("submit_echecDeLExtraction_reactiveLeBouton", () => {
      // Les deux fichiers sont bien arrivés, seule l'extraction a échoué : le
      // client doit pouvoir relancer sans redéposer ses photos.
      extractOcrData.mockReturnValue(throwError(() => ({ error: { message: 'Moteur OCR indisponible' } })));
      choisirLesDeuxFaces();

      component.submit();

      expect(component.error()).toBe('Moteur OCR indisponible');
      expect(component.sending()).toBe(false);
      expect(navigateTo).not.toHaveBeenCalled();
    });

    it('submit_echecPuisNouvelleTentative_aboutit', () => {
      // Les fichiers choisis doivent survivre à l'échec, sinon le client
      // recommence tout son dépôt pour une coupure réseau d'une seconde.
      uploadDocument.mockReturnValue(throwError(() => ({ error: { message: 'Panne passagère' } })));
      choisirLesDeuxFaces();
      component.submit();

      uploadDocument.mockReturnValue(reponse());
      component.submit();

      expect(component.front()).not.toBeNull();
      expect(component.back()).not.toBeNull();
      expect(navigateTo).toHaveBeenCalledWith('/onboarding/document-ocr-review');
    });

    it('submit_erreurSansAucunMessage_afficheUnLibelleParDefaut', () => {
      // Un échec muet laisserait le client devant un bouton redevenu actif,
      // sans savoir ce qui n'a pas marché ni ce qu'il doit changer.
      uploadDocument.mockReturnValue(throwError(() => ({})));
      choisirLesDeuxFaces();

      component.submit();

      expect(component.error()).toBe('Le traitement de la pièce d’identité a échoué.');
    });

    it("submit_erreurHttp_afficheLeMessageMetierEtNonLeLibelleTechnique", () => {
      // Le piège récurrent du projet : une HttpErrorResponse porte toujours un
      // `message` fabriqué par Angular, qui gagne si on le lit avant
      // `err.error.message`. Le client lirait une phrase technique en anglais
      // au lieu de la seule information utile, à savoir la limite de taille.
      uploadDocument.mockReturnValue(
        throwError(() => ({
          status: 413,
          message: 'Http failure response for /api/documents/upload: 413 Payload Too Large',
          error: { message: 'Fichier trop volumineux : 5 Mo maximum.' },
        })),
      );
      choisirLesDeuxFaces();

      component.submit();

      expect(component.error()).toBe('Fichier trop volumineux : 5 Mo maximum.');
    });

    it('bouton_apresUnEchec_redevientCliquable', () => {
      // Vérification sur le bouton réel et non sur les seuls signaux : c'est
      // lui que le client doit pouvoir cliquer à nouveau.
      uploadDocument.mockReturnValue(throwError(() => ({ error: { message: 'Service indisponible' } })));
      const fixture = TestBed.createComponent(DocumentUpload);
      component = fixture.componentInstance;
      fixture.detectChanges();

      choisirLesDeuxFaces();
      component.submit();
      fixture.detectChanges();

      const continuer: HTMLButtonElement = fixture.nativeElement.querySelector('.primary-button');
      expect(continuer.disabled).toBe(false);
    });

    it('pick_apresUnEchec_effaceLeMessageDevenuCaduc', () => {
      // Le client comprend que sa photo était en cause, en choisit une autre,
      // et « Fichier rejeté » restait affiché sous un champ qu'il venait
      // pourtant de corriger : rien ne lui disait que son nouveau choix était
      // recevable.
      uploadDocument.mockReturnValue(throwError(() => ({ error: { message: 'Fichier rejeté' } })));
      choisirLesDeuxFaces();
      component.submit();

      choisir('front', fichier('recto-net.jpg'));

      expect(component.error()).toBeNull();
    });
  });

  describe('fin du dépôt', () => {
    it("submit_succes_libereLeBoutonAvantDeNaviguer", () => {
      // Tant que la navigation aboutit, l'écran disparaît et un bouton resté en
      // chargement ne se voit pas. Mais `NavigationService` avale les échecs de
      // navigation : un garde qui refuse laisserait le client sur cet écran,
      // devant un bouton « Analyse qualité & extraction OCR… » définitivement
      // inerte et sans le moindre message.
      choisirLesDeuxFaces();

      component.submit();

      expect(component.sending()).toBe(false);
    });

    it("submit_pendantLEnvoi_annonceLEtapeEnCours", () => {
      // Le libellé du bouton est le seul retour visible pendant un
      // téléversement qui peut durer une minute sur un réseau mobile. Figé sur
      // « Envoi... », il ne distingue plus une progression d'un blocage.
      uploadDocument.mockReturnValue(NEVER);
      choisirLesDeuxFaces();

      component.submit();

      expect(component.statusMessage()).toBe('Téléversement du recto...');
    });
  });

  describe('retour en arrière', () => {
    it('goBack_depuisLEcran_revientALEtapePrecedente', () => {
      // Passe par l'historique et non par une route figée : cet écran est
      // atteint soit par le scan, soit directement, et le retour doit ramener
      // là d'où le client vient.
      component.goBack();

      expect(back).toHaveBeenCalled();
    });
  });
});
