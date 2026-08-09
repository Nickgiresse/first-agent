import { TestBed } from '@angular/core/testing';
import { Location } from '@angular/common';
import { of, throwError } from 'rxjs';

import { Kyc } from './kyc';
import { NavigationService } from '../../../core/services/navigation';
import { OnboardingService } from '../../../core/services/onboarding';
import { OnboardingState } from '../../../core/services/onboarding-state';

/**
 * Deuxième étape : vérification de l'adresse e-mail par code à usage unique.
 *
 * C'est le seul facteur de possession du parcours, et l'écran porte trois
 * règles que rien d'autre ne protège : le code doit faire six chiffres et rien
 * d'autre, un nouveau code ne doit pas pouvoir être demandé avant le délai de
 * 45 secondes, et un code refusé doit rendre la main au client sans le
 * bloquer. Les cas ci-dessous verrouillent ces règles, et documentent celles
 * qui ne sont aujourd'hui pas tenues.
 */
describe('Kyc', () => {
  let component: Kyc;
  let requestEmailOtp: ReturnType<typeof vi.fn>;
  let verifyEmailOtp: ReturnType<typeof vi.fn>;
  let setEmail: ReturnType<typeof vi.fn>;
  let navigateTo: ReturnType<typeof vi.fn>;
  let back: ReturnType<typeof vi.fn>;

  const EMAIL_VALIDE = 'jean.nkeng@exemple.invalid';
  const DELAI_RENVOI_MS = 45_000;

  beforeEach(() => {
    requestEmailOtp = vi.fn().mockReturnValue(of({ success: true, message: '', data: null }));
    verifyEmailOtp = vi.fn().mockReturnValue(of({ success: true, message: '', data: null }));
    setEmail = vi.fn();
    navigateTo = vi.fn();
    back = vi.fn();

    TestBed.configureTestingModule({
      imports: [Kyc],
      providers: [
        { provide: OnboardingService, useValue: { requestEmailOtp, verifyEmailOtp } },
        {
          provide: OnboardingState,
          useValue: { firstName: () => 'Jean', lastName: () => 'Nkeng', setEmail },
        },
        { provide: NavigationService, useValue: { navigateTo } },
        { provide: Location, useValue: { back } },
      ],
    });

    component = TestBed.createComponent(Kyc).componentInstance;
  });

  /** Amène l'écran en phase de saisie du code, comme après un envoi réussi. */
  function passerEnPhaseOtp(): void {
    component.email.setValue(EMAIL_VALIDE);
    component.requestCode();
  }

  it("s'affiche sur la saisie de l'adresse e-mail", () => {
    // Reprend ce que vérifiait le test généré à l'origine, en allant un pas
    // plus loin : le gabarit doit se rendre, et l'écran doit démarrer sur la
    // demande d'adresse, jamais sur un champ de code qui n'a pas été envoyé.
    const fixture = TestBed.createComponent(Kyc);
    fixture.detectChanges();

    expect(fixture.componentInstance).toBeTruthy();
    expect(fixture.componentInstance.phase()).toBe('EMAIL');
    expect(fixture.componentInstance.cooldownRemaining()).toBe(0);
  });

  describe('demande du code', () => {
    it('requestCode_adresseInvalide_nEnvoieAucunCode', () => {
      // Un envoi vers une adresse mal formée est perdu sans retour : le client
      // attendrait un code qui n'arrivera jamais.
      component.email.setValue('jean.nkeng-arobase-exemple');

      component.requestCode();

      expect(requestEmailOtp).not.toHaveBeenCalled();
      expect(component.phase()).toBe('EMAIL');
    });

    it("requestCode_adresseInvalide_rendVisibleLeMessageDeSaisie", () => {
      // Le message du gabarit est conditionné à `touched` : sans cette marque,
      // le clic resterait sans effet ni explication.
      component.email.setValue('jean.nkeng-arobase-exemple');

      component.requestCode();

      expect(component.email.touched).toBe(true);
    });

    it('requestCode_adresseVide_nEnvoieAucunCode', () => {
      component.requestCode();

      expect(requestEmailOtp).not.toHaveBeenCalled();
    });

    it('requestCode_adresseValide_envoieEtPasseALaSaisieDuCode', () => {
      component.email.setValue(EMAIL_VALIDE);

      component.requestCode();

      expect(requestEmailOtp).toHaveBeenCalledWith({ email: EMAIL_VALIDE });
      expect(component.phase()).toBe('OTP');
      expect(component.submitting()).toBe(false);
    });

    it('requestCode_dejaEnCours_nEnvoiePasUnSecondCode', () => {
      // Deux envois pour une même demande, c'est deux codes valides en
      // circulation et un client qui saisit le premier alors que le second l'a
      // remplacé.
      component.email.setValue(EMAIL_VALIDE);
      component.submitting.set(true);

      component.requestCode();

      expect(requestEmailOtp).not.toHaveBeenCalled();
    });

    it('requestCode_nouvelEnvoi_videLeCodePrecedent', () => {
      // Sans cette remise à zéro, le code de l'envoi précédent resterait dans
      // le champ et serait soumis tel quel contre le nouveau code.
      passerEnPhaseOtp();
      component.code.setValue('123456');
      component.changeEmail();
      // Le délai doit être écoulé pour que le second envoi parte réellement :
      // c'est désormais `requestCode()` qui le fait respecter. Ce groupe de
      // cas n'utilise pas de minuteurs simulés, le compteur est donc remis à
      // zéro directement.
      component.cooldownRemaining.set(0);

      component.requestCode();

      expect(component.code.value).toBe('');
    });

    it('requestCode_echec_resteSurLaSaisieDeLAdresse', () => {
      // Basculer sur l'écran du code alors que rien n'est parti ferait attendre
      // le client devant un champ qu'il ne pourra jamais remplir.
      requestEmailOtp.mockReturnValue(throwError(() => ({ error: { message: 'Service de messagerie indisponible' } })));
      component.email.setValue(EMAIL_VALIDE);

      component.requestCode();

      expect(component.phase()).toBe('EMAIL');
      expect(component.error()).toBe('Service de messagerie indisponible');
      // Le bouton doit se réarmer, sinon le client est bloqué sur un formulaire
      // mort jusqu'au rechargement de la page.
      expect(component.submitting()).toBe(false);
    });

    it('requestCode_echecPuisNouvelleTentative_aboutit', () => {
      requestEmailOtp.mockReturnValue(throwError(() => ({ error: { message: 'Panne passagère' } })));
      component.email.setValue(EMAIL_VALIDE);
      component.requestCode();

      requestEmailOtp.mockReturnValue(of({ success: true, message: '', data: null }));
      component.requestCode();

      expect(requestEmailOtp).toHaveBeenCalledTimes(2);
      expect(component.phase()).toBe('OTP');
    });

    it('requestCode_erreurSansAucunMessage_afficheUnLibelleParDefaut', () => {
      requestEmailOtp.mockReturnValue(throwError(() => ({})));
      component.email.setValue(EMAIL_VALIDE);

      component.requestCode();

      expect(component.error()).toBe('Impossible d’envoyer le code de vérification.');
    });
  });

  describe('saisie du code', () => {
    beforeEach(() => passerEnPhaseOtp());

    it('digitsOnly_saisieAvecLettres_neGardeQueLesChiffres', () => {
      // Le champ est en `type="text"` pour garder le clavier numérique et le
      // remplissage automatique du code : le filtrage doit donc être fait ici.
      component.code.setValue('12a3b4');

      component.digitsOnly();

      expect(component.code.value).toBe('1234');
    });

    it('digitsOnly_saisieAvecEspaces_lesRetire', () => {
      // Les codes copiés depuis un e-mail arrivent souvent avec une espace ou
      // un retour à la ligne collé : sans nettoyage, le code correct serait
      // compté comme une tentative ratée.
      component.code.setValue(' 123 456 ');

      component.digitsOnly();

      expect(component.code.value).toBe('123456');
    });

    it('digitsOnly_plusDeSixChiffres_tronqueASix', () => {
      component.code.setValue('12345678');

      component.digitsOnly();

      expect(component.code.value).toBe('123456');
    });

    it('code_cinqChiffres_resteInvalide', () => {
      component.code.setValue('12345');

      expect(component.code.invalid).toBe(true);
    });

    it('code_sixChiffres_devientValide', () => {
      component.code.setValue('123456');

      expect(component.code.valid).toBe(true);
    });

    it('verifyCode_codeIncomplet_nInterrogePasLeBackend', () => {
      // Chaque appel consomme une tentative côté banque : envoyer un code que
      // l'on sait invalide rapprocherait le client du verrouillage pour rien.
      component.code.setValue('123');

      component.verifyCode();

      expect(verifyEmailOtp).not.toHaveBeenCalled();
      expect(component.code.touched).toBe(true);
    });

    it('verifyCode_dejaEnCours_nEnvoiePasUneSecondeVerification', () => {
      component.code.setValue('123456');
      component.submitting.set(true);

      component.verifyCode();

      expect(verifyEmailOtp).not.toHaveBeenCalled();
    });
  });

  describe('délai avant renvoi du code', () => {
    beforeEach(() => {
      // Le décompte repose sur un intervalle : il doit être maîtrisé pour que
      // le test mesure la règle et non la patience du lanceur.
      vi.useFakeTimers();
      passerEnPhaseOtp();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('requestCode_envoiReussi_ouvreLeDelaiDeQuaranteCinqSecondes', () => {
      expect(component.cooldownRemaining()).toBe(45);
    });

    it('resendCode_pendantLeDelai_nEnvoieRien', () => {
      // La règle anti-abus de l'écran : sans elle, un clic répété déclenche
      // autant d'e-mails et autant de codes, ce qui fait tomber l'adresse du
      // client dans les filtres du fournisseur de messagerie.
      vi.advanceTimersByTime(DELAI_RENVOI_MS - 1_000);

      component.resendCode();

      expect(requestEmailOtp).toHaveBeenCalledTimes(1);
    });

    it('cooldown_uneSecondeAvantLaFin_afficheEncoreUneSeconde', () => {
      vi.advanceTimersByTime(DELAI_RENVOI_MS - 1_000);

      expect(component.cooldownRemaining()).toBe(1);
    });

    it('cooldown_delaiEcoule_retombeAZero', () => {
      // Un décompte qui n'atteindrait jamais zéro laisserait le bouton de
      // renvoi désactivé pour toujours : le client dont le code n'arrive pas
      // n'aurait plus aucune issue.
      vi.advanceTimersByTime(DELAI_RENVOI_MS);

      expect(component.cooldownRemaining()).toBe(0);
    });

    it('resendCode_apresLeDelai_envoieUnNouveauCode', () => {
      vi.advanceTimersByTime(DELAI_RENVOI_MS);

      component.resendCode();

      expect(requestEmailOtp).toHaveBeenCalledTimes(2);
      expect(component.cooldownRemaining()).toBe(45);
    });

    it('resendCode_envoiEnCours_nEnvoiePasEnDouble', () => {
      vi.advanceTimersByTime(DELAI_RENVOI_MS);
      component.submitting.set(true);

      component.resendCode();

      expect(requestEmailOtp).toHaveBeenCalledTimes(1);
    });

    it("requestCode_apresRetourAuFormulaireEmail_respecteLeDelai", () => {
      // Le contournement le plus simple : « Modifier l'adresse e-mail » puis
      // « Recevoir le code ». Tant que le contrôle du délai vivait dans
      // `resendCode()`, ces deux clics suffisaient à obtenir un code immédiat,
      // autant de fois que voulu, et la règle des 45 secondes ne protégeait
      // plus rien. Le bouton « Retour » emprunte le même chemin.
      component.changeEmail();

      component.requestCode();

      // Toujours le seul envoi initial : le second est refusé.
      expect(requestEmailOtp).toHaveBeenCalledTimes(1);
    });

    it("changeEmail_pendantLeDecompte_laisseLeDecompteCourir", () => {
      // Le décompte protège l'envoi, pas l'écran. L'arrêter au retour au
      // formulaire rendait la protection inopérante, et laissait de surcroît un
      // compteur figé à sa dernière valeur si la nouvelle demande échouait.
      vi.advanceTimersByTime(10_000);

      component.changeEmail();
      vi.advanceTimersByTime(60_000);

      // Le minuteur a poursuivi sa course et s'est éteint normalement.
      expect(component.cooldownRemaining()).toBe(0);
    });

    it('destruction_pendantLeDecompte_arreteLeMinuteur', () => {
      // Un intervalle survivant au composant continue d'écrire dans des
      // signaux abandonnés à chaque seconde, pour toute la durée de vie de
      // l'onglet.
      const fixture = TestBed.createComponent(Kyc);
      fixture.componentInstance.email.setValue(EMAIL_VALIDE);
      fixture.componentInstance.requestCode();
      const minuteursActifs = vi.getTimerCount();

      fixture.destroy();

      expect(minuteursActifs).toBeGreaterThan(0);
      expect(vi.getTimerCount()).toBe(minuteursActifs - 1);
    });
  });

  describe('vérification du code', () => {
    beforeEach(() => passerEnPhaseOtp());

    it('verifyCode_codeCorrect_enregistreLAdresseEtPasseAuPin', () => {
      component.code.setValue('123456');

      component.verifyCode();

      expect(verifyEmailOtp).toHaveBeenCalledWith({ code: '123456' });
      // L'adresse n'est enregistrée qu'une fois prouvée : la retenir avant la
      // vérification laisserait une adresse non confirmée dans le dossier.
      expect(setEmail).toHaveBeenCalledWith(EMAIL_VALIDE);
      expect(navigateTo).toHaveBeenCalledWith('/onboarding/pin-creation');
    });

    it('verifyCode_codeIncorrect_nEnregistreRienEtRendLaMain', () => {
      verifyEmailOtp.mockReturnValue(throwError(() => ({ error: { message: 'Code expiré' } })));
      component.code.setValue('000000');

      component.verifyCode();

      expect(setEmail).not.toHaveBeenCalled();
      expect(navigateTo).not.toHaveBeenCalled();
      expect(component.error()).toBe('Code expiré');
      // Sans cela, un code mal saisi condamne le parcours : le bouton reste
      // inerte et le client n'a plus qu'à tout recommencer.
      expect(component.submitting()).toBe(false);
      expect(component.phase()).toBe('OTP');
    });

    it('verifyCode_codeIncorrect_conserveLaSaisiePourCorrection', () => {
      // Vider le champ obligerait à ressaisir les six chiffres alors qu'un seul
      // est souvent en cause.
      verifyEmailOtp.mockReturnValue(throwError(() => ({ error: { message: 'Code incorrect' } })));
      component.code.setValue('123455');

      component.verifyCode();

      expect(component.code.value).toBe('123455');
    });

    it('verifyCode_erreurSansAucunMessage_afficheUnLibelleParDefaut', () => {
      verifyEmailOtp.mockReturnValue(throwError(() => ({})));
      component.code.setValue('123456');

      component.verifyCode();

      expect(component.error()).toBe('Code de vérification incorrect.');
    });

    it("verifyCode_tentativesRepetees_nEstJamaisBridePartLeFront", () => {
      // DÉFAUT DE PRODUCTION, comportement documenté tel qu'il est aujourd'hui.
      //
      // L'écran ne compte pas les échecs : rien ici n'arrête une énumération du
      // code à six chiffres. La protection repose entièrement sur le backend,
      // et si celui-ci ne verrouille pas, ou verrouille seulement par adresse
      // IP, plus rien ne freine les tentatives.
      //
      // CE QUI DEVRAIT ÊTRE : un compteur de tentatives dans le composant, qui
      // ferme la saisie et renvoie à la demande d'un nouveau code au bout de
      // quelques échecs, comme le fait déjà l'écran de code PIN.
      verifyEmailOtp.mockReturnValue(throwError(() => ({ error: { message: 'Code incorrect' } })));

      for (let essai = 0; essai < 20; essai++) {
        component.code.setValue(String(essai).padStart(6, '0'));
        component.verifyCode();
      }

      expect(verifyEmailOtp).toHaveBeenCalledTimes(20);
      expect(component.phase()).toBe('OTP');
    });

    it("verifyCode_erreurHttp_afficheLeMessageMetierEtNonLeLibelleTechnique", () => {
      // Cas le plus parlant du projet. Le composant lisait `err.message` AVANT
      // `err.error.message` ; or une HttpErrorResponse porte toujours un
      // `message` fabriqué par Angular, qui gagnait donc systématiquement.
      //
      // Le message perdu était justement celui qui annonce le verrouillage
      // après trop de tentatives : le client lisait une phrase technique en
      // anglais, sans comprendre qu'il devait attendre.
      verifyEmailOtp.mockReturnValue(
        throwError(() => ({
          status: 429,
          message: 'Http failure response for /api/onboarding/kyc/otp/verify: 429 Too Many Requests',
          error: { message: 'Trop de tentatives. Réessayez dans 15 minutes.' },
        })),
      );
      component.code.setValue('000000');

      component.verifyCode();

      expect(component.error()).toBe('Trop de tentatives. Réessayez dans 15 minutes.');
    });

    it("verifyCode_succes_libereLeBoutonAvantDeNaviguer", () => {
      // Tant que la navigation aboutit, l'écran disparaît et un bouton resté en
      // chargement ne se voit pas. Mais `NavigationService` avale les échecs de
      // navigation en les journalisant : un garde qui refuse laisserait le
      // client sur cet écran, devant un bouton « Vérification… » définitivement
      // inerte et sans le moindre message.
      component.code.setValue('123456');

      component.verifyCode();

      expect(component.submitting()).toBe(false);
    });
  });

  describe('retour en arrière', () => {
    it("goBack_pendantLaSaisieDuCode_revientAuFormulaireSansQuitterLEcran", () => {
      // Quitter l'écran ferait perdre la session au client venu simplement
      // corriger une faute de frappe dans son adresse.
      passerEnPhaseOtp();

      component.goBack();

      expect(component.phase()).toBe('EMAIL');
      expect(back).not.toHaveBeenCalled();
    });

    it("goBack_surLeFormulaireEmail_quitteLEcran", () => {
      component.goBack();

      expect(back).toHaveBeenCalled();
    });

    it('changeEmail_apresUneErreur_effaceLeMessage', () => {
      // Le message portait sur l'ancienne adresse : le laisser affiché sur le
      // formulaire réouvert ferait croire à un refus de la nouvelle saisie.
      passerEnPhaseOtp();
      component.error.set('Code expiré');

      component.changeEmail();

      expect(component.error()).toBeNull();
      expect(component.phase()).toBe('EMAIL');
    });
  });

  describe('identité affichée', () => {
    it("identite_provientDeLaSessionBancaire_etNonDUneSaisie", () => {
      // L'écran annonce au client que son identité est confirmée par sa banque.
      // Si ces valeurs venaient d'ailleurs que de la session ouverte à l'étape
      // précédente, l'affirmation serait fausse.
      expect(component.firstName()).toBe('Jean');
      expect(component.lastName()).toBe('Nkeng');
    });
  });
});
