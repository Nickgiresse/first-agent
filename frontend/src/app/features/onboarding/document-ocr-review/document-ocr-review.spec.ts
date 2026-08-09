import { TestBed } from '@angular/core/testing';
import { Location } from '@angular/common';
import { NEVER, of, throwError } from 'rxjs';

import { DocumentOcrReview } from './document-ocr-review';
import { DocumentService } from '../../../core/services/document';
import { NavigationService } from '../../../core/services/navigation';

/**
 * Relecture des données extraites de la pièce d'identité.
 *
 * C'est le dernier point du parcours où l'identité déclarée peut encore être
 * corrigée avant d'être confirmée et rapprochée du fichier client. Ce qui sort
 * d'ici est réputé lu sur le document.
 *
 * L'écran porte une règle que rien d'autre ne tient : les champs obligatoires
 * dépendent du type de pièce. Une CNI porte un numéro et une date
 * d'expiration ; un récépissé de demande n'a ni l'un ni l'autre mais porte une
 * date de paiement ; un titre provisoire tient des deux. Exiger les mauvais
 * champs bloque un client qui n'a rien à se reprocher, en exiger trop peu
 * laisse partir un dossier incomplet vers le back-office.
 */
describe('DocumentOcrReview', () => {
  let component: DocumentOcrReview;
  let getOcrData: ReturnType<typeof vi.fn>;
  let confirmOcrData: ReturnType<typeof vi.fn>;
  let navigateTo: ReturnType<typeof vi.fn>;
  let back: ReturnType<typeof vi.fn>;

  /** Extraction complète d'une CNI, telle que le moteur OCR la rend. */
  function extractionCni(surcharge: Record<string, unknown> = {}): Record<string, unknown> {
    return {
      documentOcrResultId: 'ocr-1',
      documentKind: 'CNI',
      firstName: 'Jean',
      lastName: 'Nkeng',
      documentNumber: '123456789',
      sex: 'M',
      birthDate: '1990-04-12',
      expiryDate: '2030-04-11',
      birthPlace: 'Douala',
      fatherName: 'Paul Nkeng',
      motherName: 'Marie Nkeng',
      kitNumber: null,
      requestIdentifier: null,
      paymentAmount: null,
      paymentDate: null,
      confidenceScore: 94,
      documentQualityScore: 88,
      status: 'EXTRACTED',
      provider: 'python-ocr',
      ...surcharge,
    };
  }

  /** Charge l'écran avec une extraction donnée, comme le fait l'arrivée sur la page. */
  function charger(surcharge: Record<string, unknown> = {}): void {
    getOcrData.mockReturnValue(
      of({
        success: true,
        message: '',
        data: extractionCni(surcharge),
        timestamp: '2026-08-09T10:00:00Z',
      }),
    );

    component.ngOnInit();
  }

  beforeEach(() => {
    getOcrData = vi.fn().mockReturnValue(NEVER);
    confirmOcrData = vi.fn().mockReturnValue(
      of({ success: true, message: '', data: extractionCni(), timestamp: '2026-08-09T10:00:00Z' }),
    );
    navigateTo = vi.fn();
    back = vi.fn();

    TestBed.configureTestingModule({
      imports: [DocumentOcrReview],
      providers: [
        { provide: DocumentService, useValue: { getOcrData, confirmOcrData } },
        { provide: NavigationService, useValue: { navigateTo } },
        { provide: Location, useValue: { back } },
      ],
    });

    component = TestBed.createComponent(DocumentOcrReview).componentInstance;
  });

  it("s'affiche en attente des données extraites", () => {
    // Reprend ce que vérifiait le test généré à l'origine, en allant un pas
    // plus loin : le gabarit doit se rendre, et l'écran doit démarrer en
    // chargement, jamais sur un formulaire vide que le client prendrait pour
    // une extraction ayant rendu des champs vides.
    const fixture = TestBed.createComponent(DocumentOcrReview);
    fixture.detectChanges();

    expect(fixture.componentInstance).toBeTruthy();
    expect(fixture.componentInstance.loading()).toBe(true);
    expect(fixture.componentInstance.documentKind()).toBe('UNKNOWN');
  });

  describe('chargement des données extraites', () => {
    it('ngOnInit_extractionRecue_remplitLeFormulaire', () => {
      // Le client doit relire, pas ressaisir : un formulaire qui reste vide
      // alors que l'extraction a réussi lui fait retaper à la main ce que la
      // banque a déjà lu, et chaque frappe est une occasion d'écart entre la
      // pièce et le dossier.
      charger();

      expect(component.form.getRawValue()).toEqual({
        firstName: 'Jean',
        lastName: 'Nkeng',
        documentNumber: '123456789',
        sex: 'M',
        birthDate: '1990-04-12',
        expiryDate: '2030-04-11',
        birthPlace: 'Douala',
        fatherName: 'Paul Nkeng',
        motherName: 'Marie Nkeng',
        kitNumber: '',
        requestIdentifier: '',
        paymentAmount: '',
        paymentDate: '',
      });
      expect(component.loading()).toBe(false);
    });

    it('ngOnInit_champsNonLus_lesRendVidesEtNonNuls', () => {
      // Les contrôles sont déclarés `nonNullable` : y écrire `null` les
      // ramènerait à leur valeur initiale au premier `reset`, et surtout
      // afficherait « null » dans le champ sous les yeux du client.
      charger({ documentNumber: null, sex: null, birthPlace: null });

      expect(component.form.controls.documentNumber.value).toBe('');
      expect(component.form.controls.sex.value).toBe('');
      expect(component.form.controls.birthPlace.value).toBe('');
    });

    it('ngOnInit_scores_sontArrondisPourLAffichage', () => {
      // Le bandeau annonce « Qualité document : x % ». Sans arrondi, le client
      // lirait « 87.63999999999999 % » sur un écran bancaire.
      charger({ confidenceScore: 87.64, documentQualityScore: 91.2 });

      expect(component.confidenceScore()).toBe(88);
      expect(component.qualityScore()).toBe(91);
    });

    it('ngOnInit_scoreDeQualiteAbsent_neLAffichePas', () => {
      // Certains fournisseurs OCR ne rendent pas de note de qualité. Afficher
      // « 0 % » ferait croire à un document illisible alors que rien n'a été
      // mesuré.
      charger({ documentQualityScore: null });

      expect(component.qualityScore()).toBeNull();
      expect(component.confidenceScore()).toBe(94);
    });

    it('ngOnInit_typeDeDocument_estRetenuPourLAffichage', () => {
      // Le gabarit n'affiche les champs du récépissé ou du titre provisoire que
      // sur ce signal : mal renseigné, le client ne voit pas les champs qu'on
      // lui demande pourtant de remplir.
      charger({ documentKind: 'RECEPISSE' });

      expect(component.documentKind()).toBe('RECEPISSE');
    });
  });

  describe("champs obligatoires d'une CNI", () => {
    beforeEach(() => charger());

    it('cni_extractionComplete_estConfirmable', () => {
      expect(component.form.valid).toBe(true);
    });

    it('cni_numeroDeDocumentVide_bloqueLaConfirmation', () => {
      // Le numéro de la CNI est la clé de rapprochement avec le fichier
      // client : un dossier confirmé sans lui ne peut pas être instruit et
      // repart en revue manuelle.
      component.form.controls.documentNumber.setValue('');

      expect(component.form.invalid).toBe(true);
    });

    it('cni_dateDeNaissanceVide_bloqueLaConfirmation', () => {
      component.form.controls.birthDate.setValue('');

      expect(component.form.invalid).toBe(true);
    });

    it("cni_dateDExpirationVide_bloqueLaConfirmation", () => {
      // Une pièce périmée doit être détectable : sans date d'expiration, rien
      // en aval ne peut refuser une CNI expirée.
      component.form.controls.expiryDate.setValue('');

      expect(component.form.invalid).toBe(true);
    });

    it("cni_dateDePaiementVide_nEmpecheRien", () => {
      // La date de paiement n'existe que sur un récépissé. L'exiger ici
      // bloquerait tout porteur de CNI sur un champ que le gabarit ne lui
      // affiche même pas.
      component.form.controls.paymentDate.setValue('');

      expect(component.form.valid).toBe(true);
    });

    it('cni_sexeVide_nEmpecheRien', () => {
      // Le champ est affiché pour la CNI mais reste facultatif : l'OCR le lit
      // mal sur les documents usés, et bloquer dessus ferait échouer la
      // confirmation pour une lettre.
      component.form.controls.sex.setValue('');

      expect(component.form.valid).toBe(true);
    });
  });

  describe("champs obligatoires d'un titre provisoire", () => {
    beforeEach(() =>
      charger({
        documentKind: 'TITRE_PROVISOIRE',
        documentNumber: null,
        kitNumber: 'KIT-4471',
        requestIdentifier: 'DEM-2026-0091',
      }),
    );

    it("titreProvisoire_sansNumeroDeDocument_resteConfirmable", () => {
      // Un titre provisoire ne porte pas de numéro de CNI : reprendre la règle
      // de la CNI bloquerait définitivement un client parfaitement en règle,
      // sur un champ que le gabarit ne lui affiche pas.
      expect(component.form.controls.documentNumber.value).toBe('');
      expect(component.form.valid).toBe(true);
    });

    it('titreProvisoire_dateDeNaissanceVide_bloqueLaConfirmation', () => {
      component.form.controls.birthDate.setValue('');

      expect(component.form.invalid).toBe(true);
    });

    it("titreProvisoire_dateDExpirationVide_bloqueLaConfirmation", () => {
      // Un titre provisoire a une validité courte : sans date d'expiration,
      // rien ne permet de savoir s'il était encore valable au moment de
      // l'enrôlement.
      component.form.controls.expiryDate.setValue('');

      expect(component.form.invalid).toBe(true);
    });

    it("titreProvisoire_numeroDeKitVide_nEmpecheRien", () => {
      // Comportement actuel, verrouillé tel quel : le numéro de kit et
      // l'identifiant de demande sont affichés au client pour ce type de pièce,
      // mais rien ne les exige. Le dossier peut donc partir sans la référence
      // qui permet au back-office de retrouver la demande de CNI en cours.
      component.form.controls.kitNumber.setValue('');
      component.form.controls.requestIdentifier.setValue('');

      expect(component.form.valid).toBe(true);
    });
  });

  describe("champs obligatoires d'un récépissé", () => {
    beforeEach(() =>
      charger({
        documentKind: 'RECEPISSE',
        documentNumber: null,
        birthDate: null,
        expiryDate: null,
        kitNumber: 'KIT-8812',
        requestIdentifier: 'DEM-2026-0154',
        paymentAmount: '2800',
        paymentDate: '2026-05-30',
      }),
    );

    it("recepisse_sansDateDeNaissanceNiExpiration_resteConfirmable", () => {
      // Un récépissé de demande de CNI ne porte ni date de naissance ni date
      // d'expiration. Les exiger, comme le fait la branche par défaut,
      // enfermerait le porteur d'un récépissé dans un formulaire qu'il ne peut
      // pas remplir et dont les champs ne lui sont même pas affichés.
      expect(component.form.controls.birthDate.value).toBe('');
      expect(component.form.controls.expiryDate.value).toBe('');
      expect(component.form.valid).toBe(true);
    });

    it('recepisse_dateDePaiementVide_bloqueLaConfirmation', () => {
      // C'est la date de paiement qui date la demande et permet d'établir que
      // le récépissé est encore dans sa fenêtre de validité.
      component.form.controls.paymentDate.setValue('');

      expect(component.form.invalid).toBe(true);
    });

    it("recepisse_montantPercuVide_nEmpecheRien", () => {
      component.form.controls.paymentAmount.setValue('');

      expect(component.form.valid).toBe(true);
    });

    it('recepisse_numeroDeDocumentVide_nEmpecheRien', () => {
      component.form.controls.documentNumber.setValue('');

      expect(component.form.valid).toBe(true);
    });
  });

  describe("champs obligatoires d'une pièce non reconnue", () => {
    it("typeInconnu_exigeLesDatesDeLaCni", () => {
      // Le type n'a pas été identifié par le moteur. La branche par défaut
      // applique alors les règles des pièces à durée de validité : c'est le
      // parti le plus prudent, et il est verrouillé ici pour qu'un remaniement
      // de la cascade de conditions ne le fasse pas basculer en silence vers
      // « aucun champ obligatoire ».
      charger({ documentKind: 'UNKNOWN' });

      component.form.controls.birthDate.setValue('');

      expect(component.form.invalid).toBe(true);
    });

    it("typeInconnu_neReclamePasDeNumeroDeDocument", () => {
      charger({ documentKind: 'UNKNOWN' });

      component.form.controls.documentNumber.setValue('');

      expect(component.form.valid).toBe(true);
    });
  });

  describe('identité du porteur', () => {
    it('nomEtPrenom_sontObligatoiresQuelQueSoitLeTypeDePiece', () => {
      // C'est l'identité elle-même : la confirmer vide ferait entrer un dossier
      // anonyme dans le back-office, quel que soit le document présenté.
      charger({ documentKind: 'RECEPISSE', birthDate: null, expiryDate: null, paymentDate: '2026-05-30' });

      component.form.controls.firstName.setValue('');
      expect(component.form.invalid).toBe(true);

      component.form.controls.firstName.setValue('Jean');
      component.form.controls.lastName.setValue('');
      expect(component.form.invalid).toBe(true);
    });
  });

  describe('confirmation', () => {
    it('submit_formulaireIncomplet_neConfirmeRien', () => {
      // Le bouton est désactivé dans le gabarit, mais la garde doit exister
      // aussi dans le code : une touche Entrée ou un futur remaniement du
      // gabarit ne doivent pas suffire à confirmer une identité incomplète.
      charger();
      component.form.controls.lastName.setValue('');

      component.submit();

      expect(confirmOcrData).not.toHaveBeenCalled();
    });

    it("submit_formulaireIncomplet_designeLesChampsManquants", () => {
      // Sans cela, le client voyait un bouton grisé au milieu d'une dizaine de
      // champs sans savoir lequel manquait, et la seule issue qu'il trouvait
      // était « Retour », qui lui faisait recommencer le scan de sa pièce.
      charger();
      component.form.controls.lastName.setValue('');

      component.submit();

      expect(component.form.controls.lastName.touched).toBe(true);
      expect(component.error()).not.toBeNull();
    });

    it("submit_formulaireComplet_confirmeLesDonneesRelues", () => {
      // Ce sont les valeurs du formulaire qui partent, corrections comprises,
      // et non les valeurs brutes de l'OCR : c'est tout l'objet de l'écran.
      charger();
      component.form.controls.lastName.setValue('Nkeng Bilé');

      component.submit();

      expect(confirmOcrData).toHaveBeenCalledWith(
        expect.objectContaining({ firstName: 'Jean', lastName: 'Nkeng Bilé', documentNumber: '123456789' }),
      );
      expect(navigateTo).toHaveBeenCalledWith('/onboarding/liveness-challenge');
    });

    it('submit_nouvelleTentative_effaceLErreurPrecedente', () => {
      // Sans cette remise à zéro, le message du refus précédent resterait
      // affiché sous un formulaire qui vient pourtant d'aboutir.
      charger();
      component.error.set('Impossible de confirmer ces informations.');

      component.submit();

      expect(component.error()).toBeNull();
    });

    it('submit_echec_reactiveLeBouton', () => {
      // Le cas qui bloque réellement les clients : la requête échoue et, si
      // `submitting` ne retombe pas, le bouton reste inerte jusqu'au
      // rechargement de la page, qui fait tout reperdre.
      confirmOcrData.mockReturnValue(throwError(() => ({ error: { message: 'Données refusées' } })));
      charger();

      component.submit();

      expect(component.error()).toBe('Données refusées');
      expect(component.submitting()).toBe(false);
      expect(navigateTo).not.toHaveBeenCalled();
    });

    it('submit_echec_conserveLesCorrectionsDejaSaisies', () => {
      // Vider le formulaire obligerait le client à relire et corriger une
      // seconde fois les treize champs de sa pièce.
      confirmOcrData.mockReturnValue(throwError(() => ({ error: { message: 'Service indisponible' } })));
      charger();
      component.form.controls.firstName.setValue('Jean-Pierre');

      component.submit();

      expect(component.form.controls.firstName.value).toBe('Jean-Pierre');
    });

    it('submit_erreurSansAucunMessage_afficheUnLibelleParDefaut', () => {
      confirmOcrData.mockReturnValue(throwError(() => ({})));
      charger();

      component.submit();

      expect(component.error()).toBe('Impossible de confirmer ces informations.');
    });

    it("submit_erreurHttp_afficheLeMessageMetierEtNonLeLibelleTechnique", () => {
      // Le piège récurrent du projet : une HttpErrorResponse porte toujours un
      // `message` fabriqué par Angular, qui gagne s'il est lu avant
      // `err.error.message`. Le message perdu est ici celui qui dit au client
      // que son identité ne correspond pas à celle de son compte, seule
      // information qui lui permet de comprendre qu'il doit se rendre en agence.
      confirmOcrData.mockReturnValue(
        throwError(() => ({
          status: 409,
          message: 'Http failure response for /api/documents/ocr: 409 Conflict',
          error: { message: 'Ces informations ne correspondent pas à votre compte.' },
        })),
      );
      charger();

      component.submit();

      expect(component.error()).toBe('Ces informations ne correspondent pas à votre compte.');
    });

    it("submit_succes_libereLeBoutonAvantDeNaviguer", () => {
      // Tant que la navigation
      // aboutit, l'écran disparaît et personne ne le voit. Mais
      // `NavigationService` avale les échecs de navigation en les
      // journalisant : un garde qui refuse laisserait le client sur cet écran,
      // devant un bouton « Confirmation… » définitivement inerte et sans le
      // moindre message.
      //
      charger();

      component.submit();

      expect(component.submitting()).toBe(false);
    });

    it('submit_doubleClic_neConfirmeQuUneFois', () => {
      // Avant la garde, la seule protection contre la double confirmation était l'attribut
      // `disabled` du gabarit. Le code n'a aucune garde sur `submitting()`,
      // alors que les autres écrans du parcours en ont une : sur un téléphone
      // lent, les deux clics passent avant le premier rendu et l'identité est
      // confirmée deux fois.
      //
      confirmOcrData.mockReturnValue(NEVER);
      charger();

      component.submit();
      component.submit();

      expect(confirmOcrData).toHaveBeenCalledTimes(1);
    });
  });

  describe('échec du chargement des données extraites', () => {
    function chargementEnEchec(erreur: unknown): void {
      getOcrData.mockReturnValue(throwError(() => erreur));
      component.ngOnInit();
    }

    it("ngOnInit_echec_afficheLeMessageEtSortDuChargement", () => {
      // Rester en chargement indéfiniment laisserait le client devant
      // « Chargement des données extraites… » sans fin ni explication.
      chargementEnEchec({ error: { message: 'Aucune extraction disponible' } });

      expect(component.error()).toBe('Aucune extraction disponible');
      expect(component.loading()).toBe(false);
    });

    it('ngOnInit_erreurSansAucunMessage_afficheUnLibelleParDefaut', () => {
      chargementEnEchec({});

      expect(component.error()).toBe('Impossible de récupérer les données extraites.');
    });

    it("ngOnInit_erreurHttp_afficheLeMessageMetierEtNonLeLibelleTechnique", () => {
      chargementEnEchec({
        status: 404,
        message: 'Http failure response for /api/documents/ocr: 404 Not Found',
        error: { message: 'Aucun document n’a été analysé pour cette session.' },
      });

      expect(component.error()).toBe('Aucun document n’a été analysé pour cette session.');
    });

    it("ngOnInit_echec_interditDeConfirmerUneIdentiteSaisieALaMain", () => {
      // C'est le cas le plus grave de l'écran. Quand la récupération échoue,
      // `loading` retombe et le formulaire s'affiche vide, mais aucun
      // validateur propre au type de pièce n'a été posé : ils ne sont ajoutés
      // que dans le gestionnaire de succès. Il ne reste donc que le prénom et
      // le nom, tous deux saisissables. Le client peut taper l'identité de son
      // choix et la confirmer, alors qu'aucune donnée n'a été lue sur aucun
      // document. Le back-office reçoit un dossier qu'il croit issu d'une
      // extraction OCR.
      //
      chargementEnEchec({ error: { message: 'Aucune extraction disponible' } });

      component.form.controls.firstName.setValue('Identité');
      component.form.controls.lastName.setValue('Inventée');
      component.submit();

      // Rien ne part : la confirmation est fermée tant qu'aucune extraction
      // n'a été relue.
      expect(component.extractionIndisponible()).toBe(true);
      expect(confirmOcrData).not.toHaveBeenCalled();
      expect(navigateTo).not.toHaveBeenCalled();
    });
  });

  describe('état du bouton à l’écran', () => {
    it('bouton_apresUnEchecDeConfirmation_redevientCliquable', () => {
      // Vérification sur le bouton réel et non sur les seuls signaux : c'est
      // lui que le client doit pouvoir cliquer à nouveau.
      confirmOcrData.mockReturnValue(throwError(() => ({ error: { message: 'Service indisponible' } })));
      getOcrData.mockReturnValue(
        of({
          success: true,
          message: '',
          data: extractionCni(),
          timestamp: '2026-08-09T10:00:00Z',
        }),
      );
      const fixture = TestBed.createComponent(DocumentOcrReview);
      fixture.detectChanges();

      fixture.componentInstance.submit();
      fixture.detectChanges();

      const confirmer: HTMLButtonElement = fixture.nativeElement.querySelector('.primary-button');
      expect(confirmer).not.toBeNull();
      expect(confirmer.disabled).toBe(false);
    });
  });

  describe('retour en arrière', () => {
    it('goBack_depuisLEcran_revientALEtapePrecedente', () => {
      // Passe par l'historique et non par une route figée : le client arrive
      // ici depuis le scan comme depuis le dépôt manuel, et doit repartir vers
      // celui des deux qu'il a emprunté.
      component.goBack();

      expect(back).toHaveBeenCalled();
    });
  });
});
