import { TestBed } from '@angular/core/testing';
import { Location } from '@angular/common';
import { of, throwError } from 'rxjs';

import { TermsConditions } from './terms-conditions';
import { NavigationService } from '../../../core/services/navigation';
import { OnboardingService } from '../../../core/services/onboarding';

/**
 * Écran d'acceptation des conditions générales.
 *
 * C'est le point du parcours qui porte le consentement, et la charte §22.1
 * demande 100 % de couverture sur les règles réglementaires. Les cas ci-dessous
 * verrouillent trois exigences dont la violation ne ferait échouer aucun test
 * technique : la lecture effective, l'acceptation des conditions, et le
 * consentement biométrique donné séparément.
 */
describe('TermsConditions', () => {
  let component: TermsConditions;
  let acceptTerms: ReturnType<typeof vi.fn>;
  let completeOnboarding: ReturnType<typeof vi.fn>;
  let navigateTo: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    acceptTerms = vi.fn().mockReturnValue(of({}));
    completeOnboarding = vi.fn().mockReturnValue(of({}));
    navigateTo = vi.fn();

    TestBed.configureTestingModule({
      imports: [TermsConditions],
      providers: [
        { provide: OnboardingService, useValue: { acceptTerms, completeOnboarding } },
        { provide: NavigationService, useValue: { navigateTo } },
        { provide: Location, useValue: { back: vi.fn() } },
      ],
    });

    component = TestBed.createComponent(TermsConditions).componentInstance;
  });

  /** Simule un défilement, en maîtrisant les trois mesures du cadre. */
  function defiler(scrollTop: number, clientHeight: number, scrollHeight: number): void {
    const cadre = Object.assign(document.createElement('div'), {});
    Object.defineProperties(cadre, {
      scrollTop: { value: scrollTop },
      clientHeight: { value: clientHeight },
      scrollHeight: { value: scrollHeight },
    });
    const evenement = new Event('scroll');
    Object.defineProperty(evenement, 'target', { value: cadre });

    component.onTermsScroll(evenement);
  }

  it("s'affiche avec le bouton de validation inactif", () => {
    // Reprend ce que vérifiait le test généré à l'origine, en allant un pas
    // plus loin : le gabarit doit se rendre, et l'action doit être fermée tant
    // que les conditions ne sont pas réunies.
    const fixture = TestBed.createComponent(TermsConditions);
    fixture.detectChanges();

    const valider: HTMLButtonElement | null =
      fixture.nativeElement.querySelector('button[type="submit"], .btn-primary');

    expect(fixture.componentInstance).toBeTruthy();
    if (valider) {
      expect(valider.disabled).toBe(true);
    }
  });

  describe('lecture effective', () => {
    it("n'est pas acquise tant que le bas du texte n'est pas atteint", () => {
      defiler(0, 280, 2000);

      expect(component.scrolledToEnd()).toBe(false);
    });

    it('est acquise au bas du texte', () => {
      defiler(1720, 280, 2000);

      expect(component.scrolledToEnd()).toBe(true);
    });

    it("tolère les quelques pixels que le rendu laisse en bas", () => {
      // Sans tolérance, un arrondi de rendu ou une barre de défilement
      // empêcherait durablement d'atteindre le bas exact.
      defiler(1700, 280, 2000);

      expect(component.scrolledToEnd()).toBe(true);
    });

    it("n'est jamais acquise sur un cadre non rendu", () => {
      // Toutes les mesures valent zéro, et la comparaison brute « 0 >= -24 »
      // serait vraie : la lecture serait réputée faite sans que rien n'ait été
      // lu. Ce défaut exact a déjà été rencontré sur le parcours WhatsApp.
      defiler(0, 0, 0);

      expect(component.scrolledToEnd()).toBe(false);
    });
  });

  describe('conditions de soumission', () => {
    function toutAccepter(): void {
      defiler(1720, 280, 2000);
      component.accepted.set(true);
      component.biometricConsent.set(true);
    }

    it('exige les trois conditions à la fois', () => {
      expect(component.canSubmit()).toBe(false);

      defiler(1720, 280, 2000);
      expect(component.canSubmit()).toBe(false);

      component.accepted.set(true);
      expect(component.canSubmit()).toBe(false);

      // Le consentement biométrique est distinct de l'acceptation des
      // conditions : il couvre le traitement du visage et de la pièce
      // d'identité, et ne peut pas être présumé donné avec le reste.
      component.biometricConsent.set(true);
      expect(component.canSubmit()).toBe(true);
    });

    it('refuse une seconde soumission pendant la première', () => {
      toutAccepter();
      component.busy.set(true);

      // Sans cela, un double clic déclencherait deux finalisations.
      expect(component.canSubmit()).toBe(false);
    });

    it('ne soumet rien si les conditions ne sont pas réunies', () => {
      component.accepted.set(true);
      component.biometricConsent.set(true);
      // La lecture n'a pas eu lieu.

      component.submit();

      expect(acceptTerms).not.toHaveBeenCalled();
    });

    it('enchaîne acceptation puis finalisation quand tout est réuni', () => {
      toutAccepter();

      component.submit();

      expect(acceptTerms).toHaveBeenCalledWith({ termsAccepted: true });
      expect(completeOnboarding).toHaveBeenCalled();
      expect(navigateTo).toHaveBeenCalledWith('/onboarding/success');
    });
  });

  describe('échecs', () => {
    function toutAccepter(): void {
      defiler(1720, 280, 2000);
      component.accepted.set(true);
      component.biometricConsent.set(true);
    }

    it("rend la main si l'enregistrement de l'acceptation échoue", () => {
      acceptTerms.mockReturnValue(throwError(() => ({ error: { message: 'Service indisponible' } })));
      toutAccepter();

      component.submit();

      expect(component.error()).toBe('Service indisponible');
      // `busy` doit retomber, sinon le client reste bloqué sur un bouton inerte.
      expect(component.busy()).toBe(false);
      expect(component.canSubmit()).toBe(true);
    });

    it('rend la main si la finalisation échoue, sans perdre le dossier', () => {
      completeOnboarding.mockReturnValue(
        throwError(() => ({ error: { message: 'Écriture refusée' } })),
      );
      toutAccepter();

      component.submit();

      expect(component.error()).toBe('Écriture refusée');
      expect(component.busy()).toBe(false);
      // Le client doit pouvoir réessayer sans recommencer tout le parcours.
      expect(component.canSubmit()).toBe(true);
      expect(navigateTo).not.toHaveBeenCalled();
    });
  });
});
