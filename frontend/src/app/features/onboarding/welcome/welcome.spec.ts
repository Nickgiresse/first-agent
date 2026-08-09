import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { Welcome } from './welcome';
import { LanguageService } from '../../../core/services/language';
import { OnboardingService } from '../../../core/services/onboarding';
import { OnboardingState } from '../../../core/services/onboarding-state';

/**
 * Porte d'entrée du parcours, atteinte de deux façons très différentes.
 *
 * Le client peut arriver par un lien envoyé dans la conversation WhatsApp, qui
 * porte un jeton dans l'URL, ou taper l'adresse directement. Le premier cas
 * personnalise l'accueil et, surtout, conserve le jeton qui servira tout à la
 * fin à rattacher le dossier à la bonne conversation. Le second doit rester
 * praticable de bout en bout.
 *
 * Les cas ci-dessous verrouillent la lecture du jeton, la tolérance à un lien
 * expiré, et documentent ce que le traitement actuel d'un lien refusé coûte au
 * client trois écrans plus loin.
 */
describe('Welcome', () => {
  let verifyLink: ReturnType<typeof vi.fn>;
  let setLinkToken: ReturnType<typeof vi.fn>;
  let applyFromLink: ReturnType<typeof vi.fn>;

  const JETON = 'eyJhbGciOiJIUzI1NiJ9.parcours-whatsapp';

  /** Doublure du service de langue : le gabarit s'en sert pour tous ses libellés. */
  let langue: Record<string, unknown>;

  beforeEach(() => {
    verifyLink = vi.fn().mockReturnValue(of({ data: { name: 'Jean Nkeng', lang: 'fr' } }));
    setLinkToken = vi.fn();
    applyFromLink = vi.fn();
    langue = {
      t: (fr: string) => fr,
      isEnglish: () => false,
      toggle: () => undefined,
      applyFromLink,
    };
  });

  /** Ouvre l'écran tel que l'atteint un client, avec ou sans jeton dans l'URL. */
  function ouvrirLEcran(parametres: Record<string, string> = {}): ComponentFixture<Welcome> {
    TestBed.configureTestingModule({
      imports: [Welcome],
      providers: [
        provideRouter([]),
        { provide: OnboardingService, useValue: { verifyLink } },
        { provide: OnboardingState, useValue: { setLinkToken } },
        { provide: LanguageService, useValue: langue },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(parametres) } } },
      ],
    });

    const fixture = TestBed.createComponent(Welcome);
    fixture.detectChanges();
    return fixture;
  }

  describe('entrée directe, sans lien', () => {
    it("sansJeton_nInterrogePasLaBanqueEtNeConserveRien", () => {
      // Sans jeton, l'appel partirait avec une valeur vide : le serveur le
      // rejetterait, et un jeton vide enregistré serait ensuite transmis à la
      // finalisation comme s'il en existait un.
      ouvrirLEcran();

      expect(verifyLink).not.toHaveBeenCalled();
      expect(setLinkToken).not.toHaveBeenCalled();
    });

    it('sansJeton_afficheLAccueilGenerique', () => {
      const fixture = ouvrirLEcran();

      expect(fixture.componentInstance.knownName()).toBeNull();
      expect(fixture.nativeElement.querySelector('h2').textContent).toContain('Bienvenue');
    });

    it("sansJeton_laisseLeParcoursStandardAccessible", () => {
      // C'est le seul chemin qui reste au client dont le lien a expiré : il doit
      // être présent quoi qu'il arrive à la vérification.
      const fixture = ouvrirLEcran();

      const demarrer: HTMLAnchorElement = fixture.nativeElement.querySelector(
        'a[href="/onboarding/account-verification"]',
      );

      expect(demarrer).not.toBeNull();
    });
  });

  describe('entrée par lien WhatsApp', () => {
    it('avecJeton_leConserveEtLeFaitVerifier', () => {
      // Le jeton identifie la conversation WhatsApp du client. Perdu ici, il
      // manquera à la finalisation, et le dossier ne sera rattaché à personne.
      ouvrirLEcran({ t: JETON });

      expect(setLinkToken).toHaveBeenCalledWith(JETON);
      expect(verifyLink).toHaveBeenCalledWith(JETON);
    });

    it('lienReconnu_accueilleLeClientParSonNom', () => {
      const fixture = ouvrirLEcran({ t: JETON });

      expect(fixture.componentInstance.knownName()).toBe('Jean Nkeng');
      expect(fixture.nativeElement.querySelector('h2').textContent).toContain('Jean Nkeng');
    });

    it("lienReconnuSansNom_resteSurLAccueilGenerique", () => {
      // Un accueil « Bonjour » suivi d'un vide se lit comme une anomalie sur le
      // tout premier écran, là où le client décide s'il fait confiance.
      verifyLink.mockReturnValue(of({ data: { name: null, lang: 'fr' } }));

      const fixture = ouvrirLEcran({ t: JETON });

      expect(fixture.componentInstance.knownName()).toBeNull();
      expect(fixture.nativeElement.querySelector('h2').textContent).toContain('Bienvenue');
    });

    it("lienReconnu_ouvreLeParcoursDansLaLangueDuLien", () => {
      // Le client vient d'échanger avec le bot dans sa langue. Repartir en
      // français avec un anglophone lui fait lire ses engagements contractuels
      // dans une langue qu'il n'a pas choisie.
      verifyLink.mockReturnValue(of({ data: { name: 'John Smith', lang: 'en' } }));

      ouvrirLEcran({ t: JETON });

      expect(applyFromLink).toHaveBeenCalledWith('en');
    });

    it("reponseSansDonnees_neCassePasLEcranDEntree", () => {
      // Une réponse mal formée sur le premier écran couperait l'accès au
      // parcours entier, y compris pour les clients qui n'ont pas de lien.
      verifyLink.mockReturnValue(of(null));

      const fixture = ouvrirLEcran({ t: JETON });

      expect(fixture.componentInstance.knownName()).toBeNull();
      expect(applyFromLink).toHaveBeenCalledWith(undefined);
    });
  });

  describe('lien expiré ou refusé', () => {
    beforeEach(() => {
      verifyLink.mockReturnValue(throwError(() => ({ error: { message: 'Lien expiré' } })));
    });

    it('lienRefuse_laisseLeParcoursStandardAccessible', () => {
      // Un lien vieux de quelques jours est le cas ordinaire, pas un incident.
      // Bloquer l'écran d'accueil dessus fermerait la porte à un client dont le
      // seul tort est d'avoir répondu tardivement au bot.
      const fixture = ouvrirLEcran({ t: JETON });

      expect(fixture.componentInstance.knownName()).toBeNull();
      expect(
        fixture.nativeElement.querySelector('a[href="/onboarding/account-verification"]'),
      ).not.toBeNull();
    });

    it("lienRefuse_nAppliqueAucuneLangue", () => {
      // La langue d'un lien refusé ne prouve rien : l'appliquer reviendrait à
      // faire confiance au contenu d'un jeton que la banque vient d'écarter.
      ouvrirLEcran({ t: JETON });

      expect(applyFromLink).not.toHaveBeenCalled();
    });

    it("lienRefuse_neConserveAucunJetonQuiFeraitEchouerLaFinalisation", () => {
      // Le jeton était enregistré AVANT vérification et rien ne l'effaçait en
      // cas d'échec. Le client poursuivait un parcours d'apparence normale,
      // franchissait le compte, le PIN, la pièce d'identité, la vivacité et les
      // conditions générales, puis la finalisation renvoyait ce jeton périmé au
      // WhatsApp banking : écriture refusée, transaction annulée, tout le
      // travail perdu au dernier écran.
      //
      // Le parcours ouvert sans lien du tout, lui, aboutissait : c'était la
      // présence du jeton invalide qui provoquait l'échec.
      ouvrirLEcran({ t: JETON });

      expect(setLinkToken).not.toHaveBeenCalled();
    });
  });
});
