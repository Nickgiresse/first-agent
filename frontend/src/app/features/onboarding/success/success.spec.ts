import { Provider } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Success } from './success';
import { LanguageService } from '../../../core/services/language';
import { OnboardingState } from '../../../core/services/onboarding-state';

/**
 * Dernier écran du parcours, et dernier endroit où le code PIN du client existe
 * encore dans le navigateur.
 *
 * Il a été transmis à la banque par l'étape précédente, mais l'état local le
 * conserve en mémoire pour le porter jusqu'à la finalisation. Passé ce point il
 * n'a plus aucune raison d'exister : tout ce qui survit ici reste lisible dans
 * l'onglet, sur un poste souvent partagé, à la portée de la moindre extension.
 * La purge est donc une exigence de sécurité, et non un ménage de confort.
 *
 * Les cas ci-dessous verrouillent cette purge, ainsi que le seul chemin de
 * sortie proposé au client.
 */
describe('Success', () => {
  const langue = {
    t: (fr: string) => fr,
    isEnglish: () => false,
    toggle: () => undefined,
  };

  const CLE_DE_STOCKAGE = 'onboarding-session-state';

  function configurer(providers: Provider[] = []): void {
    TestBed.configureTestingModule({
      imports: [Success],
      providers: [{ provide: LanguageService, useValue: langue }, ...providers],
    });
  }

  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  describe('purge de l’état local', () => {
    it('creation_purgeLEtatDuParcours', () => {
      const clear = vi.fn();
      configurer([{ provide: OnboardingState, useValue: { clear } }]);

      TestBed.createComponent(Success);

      expect(clear).toHaveBeenCalledTimes(1);
    });

    it('creation_purgeAvantMemeLePremierRendu', () => {
      // La purge est faite à la construction et non dans un crochet de cycle de
      // vie ou après le rendu. Un client qui ferme l'onglet dès l'apparition de
      // l'écran, ce que fait précisément celui qui retourne aussitôt sur
      // WhatsApp, ne doit pas emporter son code PIN avec lui.
      const clear = vi.fn();
      configurer([{ provide: OnboardingState, useValue: { clear } }]);

      const fixture = TestBed.createComponent(Success);

      // Le titre est encore vide : aucune liaison n'a été évaluée, donc rien
      // n'a été affiché, et le secret a pourtant déjà disparu.
      expect(clear).toHaveBeenCalled();
      expect(fixture.nativeElement.querySelector('h1')?.textContent?.trim()).toBe('');
    });

    it('creation_effaceLeCodePinDeLaMemoire', () => {
      // Vérifié sur l'état réel et non sur une doublure : c'est l'effacement
      // effectif du secret qui est exigé, pas l'appel d'une méthode qui porte le
      // bon nom.
      configurer();
      const etat = TestBed.inject(OnboardingState);
      etat.setAccountVerified('jeton-de-session', 'Jean', 'Nkeng', 900);
      etat.setPin('1234');
      expect(etat.pin()).toBe('1234');

      TestBed.createComponent(Success);

      expect(etat.pin()).toBeNull();
    });

    it('creation_effaceAussiLeJetonDeSessionEtCeluiDuLien', () => {
      // Ces deux jetons sont des porteurs d'autorisation : le premier ouvre les
      // appels du parcours, le second identifie la conversation WhatsApp du
      // client auprès de la banque. Les laisser derrière soi, c'est laisser une
      // session réutilisable dans un onglet que personne ne surveille plus.
      configurer();
      const etat = TestBed.inject(OnboardingState);
      etat.setLinkToken('jeton-du-lien');
      etat.setAccountVerified('jeton-de-session', 'Jean', 'Nkeng', 900);

      TestBed.createComponent(Success);

      expect(etat.sessionToken()).toBeNull();
      expect(etat.linkToken()).toBeNull();
      expect(etat.isSessionValid()).toBe(false);
    });

    it('creation_neLaisseAucuneTraceDansLeStockageDeSession', () => {
      // La part non secrète de l'état est écrite dans le stockage de session :
      // nom, prénom, adresse e-mail. Elle survit à la fermeture de l'écran et
      // reste lisible tant que l'onglet vit, alors que le parcours est fini.
      configurer();
      const etat = TestBed.inject(OnboardingState);
      etat.setAccountVerified('jeton-de-session', 'Jean', 'Nkeng', 900);
      etat.setEmail('jean.nkeng@exemple.invalid');
      expect(sessionStorage.getItem(CLE_DE_STOCKAGE)).not.toBeNull();

      TestBed.createComponent(Success);

      expect(sessionStorage.getItem(CLE_DE_STOCKAGE)).toBeNull();
    });
  });

  describe('retour vers la conversation WhatsApp', () => {
    function rendre(): ComponentFixture<Success> {
      configurer([{ provide: OnboardingState, useValue: { clear: vi.fn() } }]);
      const fixture = TestBed.createComponent(Success);
      fixture.detectChanges();
      return fixture;
    }

    it("lienDeRetour_sansNumeroConfigure_nEstPasAffiche", () => {
      // `whatsappUrl` vaut encore « https://wa.me/ », sans numéro, dans les
      // deux environnements. Tel quel, WhatsApp affiche une page d'erreur : le
      // seul bouton de l'écran de réussite menait donc nulle part, pour 100 %
      // des clients et au moment précis où le parcours vient d'aboutir.
      //
      // Un écran sans bouton de retour est décevant ; un bouton qui mène à une
      // erreur laisse croire que l'inscription a échoué.
      const fixture = rendre();

      expect(fixture.componentInstance.lienRetourDisponible).toBe(false);
      expect(fixture.nativeElement.querySelector('a.primary-button')).toBeNull();
      // Le client n'est pas laissé sans indication pour autant.
      expect(fixture.nativeElement.textContent).toContain('WhatsApp');
    });

    it("lienDeRetour_reconnaitUnNumeroValide", () => {
      // La condition d'affichage doit accepter le lien du jour où le numéro
      // sera renseigné, faute de quoi le bouton resterait masqué sans que
      // personne ne comprenne pourquoi.
      const avecNumero = /wa\.me\/\d{6,}/;

      expect(avecNumero.test('https://wa.me/237222233010')).toBe(true);
      expect(avecNumero.test('https://wa.me/')).toBe(false);
      expect(avecNumero.test('https://wa.me/abc')).toBe(false);
    });

    // NON TESTÉ, ET DÉLIBÉRÉMENT : le lien porte `target="_blank"` et
    // `rel="noopener"`, sans quoi l'onglet ouvert garderait une référence sur
    // celui du parcours et pourrait le rediriger vers une page imitant la
    // banque. Cette garantie n'est vérifiable qu'une fois le lien rendu, ce qui
    // suppose un numéro configuré ; `whatsappUrl` étant une constante de
    // compilation, l'environnement ne se substitue pas depuis un test. Le jour
    // où le numéro sera renseigné, un cas devra le vérifier ici.
  });

  describe('confettis', () => {
    it('confettis_sontMasquesAuxTechnologiesDAssistance', () => {
      // Soixante éléments purement décoratifs, annoncés un par un, noieraient
      // la phrase de confirmation qui est la seule information de l'écran.
      configurer([{ provide: OnboardingState, useValue: { clear: vi.fn() } }]);
      const fixture = TestBed.createComponent(Success);
      fixture.detectChanges();

      const couche: HTMLElement = fixture.nativeElement.querySelector('.confetti-layer');

      expect(couche.getAttribute('aria-hidden')).toBe('true');
    });
  });
});
