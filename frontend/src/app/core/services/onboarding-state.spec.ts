import { TestBed } from '@angular/core/testing';

import { OnboardingState } from './onboarding-state';

const STORAGE_KEY = 'onboarding-session-state';

/**
 * Ce service porte le code PIN et la validité de session.
 *
 * La charte frontend §22.1 demande 100 % de couverture sur les règles
 * réglementaires : c'est ici qu'elles se trouvent. Les cas ci-dessous ne
 * cherchent pas un pourcentage, ils verrouillent des propriétés dont la
 * violation ne se verrait pas autrement.
 */
describe('OnboardingState', () => {
  let service: OnboardingState;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({});
    service = TestBed.inject(OnboardingState);
  });

  function stored(): Record<string, unknown> {
    return JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? '{}');
  }

  describe('code PIN', () => {
    it("n'est jamais écrit dans le stockage du navigateur", () => {
      service.setPin('1234');

      // Le cœur du sujet. Un PIN persisté reste lisible dans l'onglet bien
      // après la finalisation : extensions, script injecté, outils de
      // développement sur un poste partagé. La charte l'interdit à trois
      // endroits (§12.4, §13.2, §13.4).
      expect(stored()['pin']).toBeUndefined();
      expect(sessionStorage.getItem(STORAGE_KEY)).not.toContain('1234');
    });

    it('reste disponible en mémoire pour la finalisation', () => {
      service.setPin('1234');

      // Ne pas persister ne veut pas dire ne pas conserver : le parcours en a
      // besoin jusqu'à l'envoi.
      expect(service.pin()).toBe('1234');
    });

    it("n'est pas écrit non plus lors des autres enregistrements d'état", () => {
      service.setPin('1234');

      // Chaque appel réécrit le stockage : une seule méthode qui oublierait le
      // filtrage suffirait à faire fuiter le PIN.
      service.setEmail('client@exemple.invalid');
      service.setLinkToken('jeton-de-lien');
      service.setAccountVerified('jeton', 'Jean', 'Nkeng', 600);

      expect(sessionStorage.getItem(STORAGE_KEY)).not.toContain('1234');
      expect(service.pin()).toBe('1234');
    });

    it('survit à la vérification du compte, qui intervient après lui', () => {
      service.setPin('1234');
      service.setLinkToken('jeton-de-lien');

      service.setAccountVerified('jeton', 'Jean', 'Nkeng', 600);

      // L'entrée par lien précède la vérification : perdre ces deux valeurs
      // rendrait la finalisation impossible.
      expect(service.pin()).toBe('1234');
      expect(service.linkToken()).toBe('jeton-de-lien');
    });
  });

  describe('validité de session', () => {
    it('est invalide sans jeton', () => {
      expect(service.isSessionValid()).toBe(false);
    });

    it('est valide tant que la date limite est à venir', () => {
      service.setAccountVerified('jeton', 'Jean', 'Nkeng', 600);

      expect(service.isSessionValid()).toBe(true);
    });

    it('est invalide une fois la date limite passée', () => {
      // Durée négative : la limite est déjà derrière nous.
      service.setAccountVerified('jeton', 'Jean', 'Nkeng', -1);

      expect(service.isSessionValid()).toBe(false);
    });

    it("est invalide à l'instant exact de l'expiration", () => {
      // La comparaison est stricte : à l'instant pile, la session est finie.
      // Une inégalité large laisserait passer une session expirée.
      service.setAccountVerified('jeton', 'Jean', 'Nkeng', 0);

      expect(service.isSessionValid()).toBe(false);
    });
  });

  describe('purge', () => {
    it('efface la mémoire et le stockage', () => {
      service.setAccountVerified('jeton', 'Jean', 'Nkeng', 600);
      service.setPin('1234');

      service.clear();

      expect(service.sessionToken()).toBeNull();
      expect(service.pin()).toBeNull();
      expect(service.isSessionValid()).toBe(false);
      expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
    });
  });

  describe('relecture du stockage', () => {
    it('reprend une session écrite précédemment', () => {
      service.setAccountVerified('jeton', 'Jean', 'Nkeng', 600);

      // Un nouvel exemplaire simule le rechargement de la page.
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({});
      const repris = TestBed.inject(OnboardingState);

      expect(repris.sessionToken()).toBe('jeton');
      expect(repris.firstName()).toBe('Jean');
      // Le PIN, lui, est perdu : c'est le comportement voulu. Mieux vaut le
      // redemander que le laisser traîner d'une session à l'autre.
      expect(repris.pin()).toBeNull();
    });

    it('repart d\'un état vierge si le stockage est illisible', () => {
      sessionStorage.setItem(STORAGE_KEY, 'ceci-n-est-pas-du-json');

      TestBed.resetTestingModule();
      TestBed.configureTestingModule({});
      const repris = TestBed.inject(OnboardingState);

      // Un stockage corrompu ne doit pas empêcher le parcours de démarrer.
      expect(repris.sessionToken()).toBeNull();
      expect(repris.isSessionValid()).toBe(false);
    });
  });
});
