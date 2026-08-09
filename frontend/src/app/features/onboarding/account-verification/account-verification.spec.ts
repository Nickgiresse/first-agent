import { TestBed } from '@angular/core/testing';
import { Location } from '@angular/common';
import { of, throwError } from 'rxjs';

import { AccountVerification } from './account-verification';
import { AccountService } from '../../../core/services/account';
import { NavigationService } from '../../../core/services/navigation';
import { OnboardingState } from '../../../core/services/onboarding-state';

/**
 * Première étape du parcours : le client saisit les 18 derniers chiffres de son
 * numéro de compte, et c'est cette saisie qui ouvre la session d'onboarding.
 *
 * Tout ce qui suit dépend de cet écran. Un numéro mal filtré part vers le
 * système bancaire, un bouton qui ne se réarme pas après un refus laisse le
 * client devant un formulaire mort, et une session ouverte sur un compte non
 * éligible fait entrer dans le parcours quelqu'un qui n'aurait pas dû y entrer.
 * Aucune de ces trois fautes ne ferait échouer un test technique.
 */
describe('AccountVerification', () => {
  let component: AccountVerification;
  let verifyAccount: ReturnType<typeof vi.fn>;
  let setAccountVerified: ReturnType<typeof vi.fn>;
  let navigateTo: ReturnType<typeof vi.fn>;
  let back: ReturnType<typeof vi.fn>;

  const COMPTE_VALIDE = '123456789012345678';

  /** Réponse du service de vérification, dans la forme rendue par le backend. */
  function reponse(
    corps: Record<string, unknown> | null,
    options: { success?: boolean; message?: string } = {},
  ) {
    return of({
      success: options.success ?? true,
      message: options.message ?? '',
      data: corps,
      timestamp: '2026-08-09T10:00:00Z',
    });
  }

  function compteEligible(): ReturnType<typeof reponse> {
    return reponse({
      eligible: true,
      firstName: 'Jean',
      lastName: 'Nkeng',
      sessionToken: 'jeton-de-session',
      expiresInSeconds: 600,
    });
  }

  beforeEach(() => {
    verifyAccount = vi.fn().mockReturnValue(compteEligible());
    setAccountVerified = vi.fn();
    navigateTo = vi.fn();
    back = vi.fn();

    TestBed.configureTestingModule({
      imports: [AccountVerification],
      providers: [
        { provide: AccountService, useValue: { verifyAccount } },
        { provide: NavigationService, useValue: { navigateTo } },
        { provide: OnboardingState, useValue: { setAccountVerified } },
        { provide: Location, useValue: { back } },
      ],
    });

    component = TestBed.createComponent(AccountVerification).componentInstance;
  });

  it("s'affiche avec le bouton de validation inactif", () => {
    // Reprend ce que vérifiait le test généré à l'origine, en allant un pas
    // plus loin : le gabarit doit se rendre, et l'action doit rester fermée
    // tant qu'aucun numéro n'a été saisi.
    const fixture = TestBed.createComponent(AccountVerification);
    fixture.detectChanges();

    const continuer: HTMLButtonElement | null =
      fixture.nativeElement.querySelector('.primary-button');

    expect(fixture.componentInstance).toBeTruthy();
    expect(continuer).not.toBeNull();
    expect(continuer!.disabled).toBe(true);
  });

  describe('validation du numéro de compte', () => {
    it('valid_numeroIncomplet_resteFaux', () => {
      // 17 chiffres partent quand même vers le système bancaire si rien ne les
      // arrête ici : la réponse serait un refus incompréhensible pour le client.
      component.onAccountDigitsChange('12345678901234567');

      expect(component.valid()).toBe(false);
    });

    it('valid_dixHuitChiffres_devientVrai', () => {
      component.onAccountDigitsChange(COMPTE_VALIDE);

      expect(component.valid()).toBe(true);
    });

    it('valid_numeroTropLong_resteFaux', () => {
      // Le champ enfant tronque déjà à 18, mais cet écran ne doit pas dépendre
      // de lui : le jour où le numéro arrive d'un collage, d'un lien ou d'un
      // autre composant, la garde doit tenir seule.
      component.onAccountDigitsChange('1234567890123456789');

      expect(component.valid()).toBe(false);
    });

    it('valid_dixHuitCaracteresDontUneLettre_resteFaux', () => {
      // Compter les caractères au lieu des chiffres laisserait passer un numéro
      // non numérique, rejeté bien plus loin dans la chaîne.
      component.onAccountDigitsChange('12345678901234567X');

      expect(component.valid()).toBe(false);
    });

    it('valid_numeroFormateAvecEspaces_resteFaux', () => {
      // La valeur affichée est groupée « 12345 67890123456 78 ». Si un jour
      // c'est elle qui remonte au lieu des chiffres bruts, le compte partirait
      // au backend avec ses espaces : mieux vaut un refus immédiat et visible.
      component.onAccountDigitsChange('12345 67890123456 78');

      expect(component.valid()).toBe(false);
    });

    it('valid_champVide_resteFaux', () => {
      component.onAccountDigitsChange('');

      expect(component.valid()).toBe(false);
    });
  });

  describe('garde de soumission', () => {
    it('submit_numeroInvalide_nAppellePasLeService', () => {
      // Le bouton est désactivé dans le gabarit, mais la garde doit exister
      // aussi dans le code : une touche Entrée ou un futur remaniement du
      // gabarit ne doivent pas suffire à interroger la banque avec un numéro
      // incomplet.
      component.onAccountDigitsChange('123');

      component.submit();

      expect(verifyAccount).not.toHaveBeenCalled();
    });

    it("submit_numeroInvalide_rendVisibleLeMessageDeSaisie", () => {
      // Sans `touched`, le client ne verrait aucune explication : un clic sans
      // effet et rien à l'écran.
      component.submit();

      expect(component.touched()).toBe(true);
    });

    it('submit_dejaEnCours_nEnvoiePasDeSecondeRequete', () => {
      // Un double clic ouvrirait deux sessions pour le même compte, la seconde
      // écrasant la première.
      component.onAccountDigitsChange(COMPTE_VALIDE);
      component.submitting.set(true);

      component.submit();

      expect(verifyAccount).not.toHaveBeenCalled();
    });

    it('submit_numeroValide_transmetLesChiffresBruts', () => {
      component.onAccountDigitsChange(COMPTE_VALIDE);

      component.submit();

      expect(verifyAccount).toHaveBeenCalledWith(COMPTE_VALIDE);
    });
  });

  describe('compte refusé par la banque', () => {
    it('submit_compteNonEligible_nOuvrePasDeSession', () => {
      // Le point le plus sensible de l'écran : un compte clos, bloqué ou déjà
      // enrôlé ne doit ouvrir aucune session, sinon le parcours continue pour
      // quelqu'un qui n'y a pas droit.
      verifyAccount.mockReturnValue(
        reponse(
          { eligible: false, firstName: '', lastName: '', sessionToken: '', expiresInSeconds: 0 },
          { message: 'Ce compte est déjà enrôlé.' },
        ),
      );
      component.onAccountDigitsChange(COMPTE_VALIDE);

      component.submit();

      expect(setAccountVerified).not.toHaveBeenCalled();
      expect(navigateTo).not.toHaveBeenCalled();
      expect(component.error()).toBe('Ce compte est déjà enrôlé.');
      // Le bouton doit se réarmer : le client peut avoir saisi le compte d'un
      // proche par erreur et vouloir corriger.
      expect(component.submitting()).toBe(false);
    });

    it('submit_reponseEnEchecSansDonnees_afficheLeMessageSansPlanter', () => {
      // Sur un échec, le backend renvoie souvent `data` à vide. Le contrôle de
      // `success` doit rester le premier de la condition : inversé, la lecture
      // de `data.eligible` lèverait une exception dans le gestionnaire de
      // souscription, le bouton resterait en chargement et le client n'aurait
      // ni message ni moyen de recommencer.
      verifyAccount.mockReturnValue(
        reponse(null, { success: false, message: 'Compte introuvable.' }),
      );
      component.onAccountDigitsChange(COMPTE_VALIDE);

      component.submit();

      expect(component.error()).toBe('Compte introuvable.');
      expect(component.submitting()).toBe(false);
      expect(setAccountVerified).not.toHaveBeenCalled();
    });

    it('submit_refusSansMessage_afficheUnLibelleParDefaut', () => {
      // Un refus muet laisserait la zone d'erreur vide : le client verrait le
      // bouton redevenir actif sans savoir pourquoi rien ne s'est passé.
      verifyAccount.mockReturnValue(
        reponse({ eligible: false, firstName: '', lastName: '', sessionToken: '', expiresInSeconds: 0 }),
      );
      component.onAccountDigitsChange(COMPTE_VALIDE);

      component.submit();

      expect(component.error()).toBe('Ce compte ne peut pas être utilisé.');
    });
  });

  describe('compte accepté', () => {
    it('submit_compteEligible_ouvreLaSessionAvecLesDonneesDeLaBanque', () => {
      component.onAccountDigitsChange(COMPTE_VALIDE);

      component.submit();

      // L'identité vient de la banque, jamais d'une saisie : c'est l'argument
      // même de l'écran, et l'ordre des quatre valeurs porte le nom, le prénom
      // et la durée de validité de la session.
      expect(setAccountVerified).toHaveBeenCalledWith('jeton-de-session', 'Jean', 'Nkeng', 600);
      expect(navigateTo).toHaveBeenCalledWith('/onboarding/kyc');
      expect(component.error()).toBeNull();
    });

    it('submit_nouvelleTentative_effaceLErreurPrecedente', () => {
      // Sans cette remise à zéro, le message du refus précédent resterait
      // affiché sous un formulaire qui vient pourtant d'aboutir.
      component.error.set('Compte introuvable.');
      component.onAccountDigitsChange(COMPTE_VALIDE);

      component.submit();

      expect(component.error()).toBeNull();
    });
  });

  describe('échec technique', () => {
    it('submit_erreurReseau_reactiveLeBouton', () => {
      // Le cas qui bloque réellement les clients : la requête échoue, et si
      // `submitting` ne retombe pas, le bouton reste inerte jusqu'au
      // rechargement de la page. Le client abandonne le parcours.
      verifyAccount.mockReturnValue(throwError(() => ({ error: { message: 'Service indisponible' } })));
      component.onAccountDigitsChange(COMPTE_VALIDE);

      component.submit();

      expect(component.submitting()).toBe(false);
      expect(component.error()).toBe('Service indisponible');
      expect(navigateTo).not.toHaveBeenCalled();
    });

    it('submit_apresUnEchec_autoriseUneNouvelleTentative', () => {
      verifyAccount.mockReturnValue(throwError(() => ({ error: { message: 'Service indisponible' } })));
      component.onAccountDigitsChange(COMPTE_VALIDE);
      component.submit();

      verifyAccount.mockReturnValue(compteEligible());
      component.submit();

      expect(verifyAccount).toHaveBeenCalledTimes(2);
      expect(navigateTo).toHaveBeenCalledWith('/onboarding/kyc');
    });

    it('submit_erreurSansAucunMessage_afficheUnLibelleParDefaut', () => {
      verifyAccount.mockReturnValue(throwError(() => ({})));
      component.onAccountDigitsChange(COMPTE_VALIDE);

      component.submit();

      expect(component.error()).toBe('Impossible de vérifier ce compte. Veuillez réessayer.');
      expect(component.submitting()).toBe(false);
    });

    it("submit_erreurHttp_afficheLeMessageMetierEtNonLeLibelleTechnique", () => {
      // Le composant lisait `err.message` AVANT `err.error.message`. Or une
      // HttpErrorResponse porte toujours un `message` fabriqué par Angular du
      // type « Http failure response for ... : 403 Forbidden » : il gagnait
      // systématiquement, et le message métier n'atteignait jamais le client,
      // qui lisait une phrase technique en anglais sur un écran bancaire.
      verifyAccount.mockReturnValue(
        throwError(() => ({
          status: 403,
          message: 'Http failure response for /api/accounts/verify: 403 Forbidden',
          error: { message: 'Ce compte fait l’objet d’une opposition.' },
        })),
      );
      component.onAccountDigitsChange(COMPTE_VALIDE);

      component.submit();

      expect(component.error()).toBe('Ce compte fait l’objet d’une opposition.');
    });

    it("onAccountDigitsChange_apresUneErreur_effaceLeMessageDevenuCaduc", () => {
      // Sans cela, « Compte introuvable. » restait affiché sous un champ que le
      // client venait de corriger, lui laissant croire que sa nouvelle saisie
      // était refusée elle aussi.
      verifyAccount.mockReturnValue(
        reponse(null, { success: false, message: 'Compte introuvable.' }),
      );
      component.onAccountDigitsChange(COMPTE_VALIDE);
      component.submit();

      component.onAccountDigitsChange('987654321098765432');

      expect(component.error()).toBeNull();
    });
  });

  describe('état du bouton à l’écran', () => {
    it('bouton_apresUnEchec_redevientCliquable', () => {
      // Vérification au niveau du gabarit et non des signaux seuls : c'est le
      // bouton réel qui doit redevenir utilisable, sinon le client est bloqué
      // quoi que disent les signaux.
      verifyAccount.mockReturnValue(throwError(() => ({ error: { message: 'Service indisponible' } })));
      const fixture = TestBed.createComponent(AccountVerification);
      fixture.detectChanges();

      fixture.componentInstance.onAccountDigitsChange(COMPTE_VALIDE);
      fixture.componentInstance.submit();
      fixture.detectChanges();

      const continuer: HTMLButtonElement = fixture.nativeElement.querySelector('.primary-button');
      expect(continuer.disabled).toBe(false);
    });
  });

  describe('retour en arrière', () => {
    it("goBack_depuisLEcran_revientALEtapePrecedente", () => {
      // Passe par l'historique et non par une route figée : le parcours est
      // atteignable depuis le lien WhatsApp comme depuis l'écran d'accueil.
      component.goBack();

      expect(back).toHaveBeenCalled();
    });
  });
});
