import { TestBed } from '@angular/core/testing';

import { LanguageService } from './language';

const STORAGE_KEY = 'onboarding-lang';

/**
 * Parcours bilingue : le CGU et le cahier des charges imposent que le client
 * puisse mener l'ouverture de compte en anglais, et que son choix soit
 * mémorisé pour le bot et les reçus.
 *
 * Une régression ici ne casse rien de visible côté développeur, qui travaille
 * en français : elle se manifeste chez un client anglophone par un écran qui
 * repasse en français au rechargement, ou par un libellé vide. Ces cas
 * verrouillent l'ordre de résolution de la langue et le comportement quand une
 * traduction manque.
 */
describe('LanguageService', () => {
  /**
   * La langue initiale est résolue à la construction du service. Les cas qui
   * portent sur cette résolution doivent donc préparer l'environnement puis
   * fabriquer un service neuf, comme au chargement de la page.
   */
  function creerService(): LanguageService {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({});
    return TestBed.inject(LanguageService);
  }

  function forcerLangueNavigateur(valeur: string | undefined): void {
    Object.defineProperty(navigator, 'language', { value: valeur, configurable: true });
  }

  beforeEach(() => {
    localStorage.clear();
    document.documentElement.lang = '';
    // Navigateur francophone par défaut : les cas qui ne parlent pas du
    // navigateur ne doivent pas dépendre de la machine qui exécute la suite.
    forcerLangueNavigateur('fr-FR');
  });

  afterEach(() => {
    vi.restoreAllMocks();
    // Rend la propriété d'origine portée par le prototype.
    delete (navigator as unknown as Record<string, unknown>)['language'];
    localStorage.clear();
  });

  describe('résolution initiale', () => {
    it('reprend le choix mémorisé, qui prime sur la langue du navigateur', () => {
      localStorage.setItem(STORAGE_KEY, 'en');
      forcerLangueNavigateur('fr-FR');

      // Un client anglophone sur un poste configuré en français a fait un choix
      // explicite : le lui reprendre à chaque écran rendrait le parcours
      // inutilisable.
      expect(creerService().current()).toBe('en');
    });

    it('suit le navigateur anglophone en l\'absence de choix mémorisé', () => {
      forcerLangueNavigateur('en-GB');

      // La comparaison porte sur le préfixe : `en-GB`, `en-US` et `EN` doivent
      // tous mener à l'anglais.
      expect(creerService().current()).toBe('en');
      expect(creerService().isEnglish()).toBe(true);
    });

    it('accepte une langue de navigateur écrite en majuscules', () => {
      forcerLangueNavigateur('EN-US');

      expect(creerService().current()).toBe('en');
    });

    it('retombe sur le français pour toute autre langue', () => {
      forcerLangueNavigateur('de-DE');

      // Le français est la langue par défaut de la banque : mieux vaut un
      // parcours en français qu'un parcours vide.
      expect(creerService().current()).toBe('fr');
    });

    it('démarre en français quand le navigateur n\'annonce aucune langue', () => {
      forcerLangueNavigateur(undefined);

      // Certains navigateurs embarqués (vue web WhatsApp, par exemple) ne
      // renseignent pas la langue. Une lecture non protégée lèverait ici, et le
      // service étant construit à la racine, l'application entière ne
      // démarrerait pas.
      expect(() => creerService()).not.toThrow();
      expect(creerService().current()).toBe('fr');
    });

    it('ignore une valeur de stockage qui n\'est pas une langue connue', () => {
      localStorage.setItem(STORAGE_KEY, 'es');
      forcerLangueNavigateur('en-US');

      // Le stockage est modifiable par n'importe quel script de l'onglet. Une
      // valeur inattendue doit être écartée, pas propagée dans `document.lang`
      // ni dans les appels au bot.
      expect(creerService().current()).toBe('en');
    });

    it('démarre malgré un stockage inaccessible', () => {
      vi.spyOn(localStorage, 'getItem').mockImplementation(() => {
        throw new Error('accès au stockage refusé');
      });
      forcerLangueNavigateur('en-US');

      // Navigation privée ou cookies tiers bloqués : la lecture lève. Sans
      // protection, l'application ne se lance pas du tout.
      expect(creerService().current()).toBe('en');
    });
  });

  describe('set', () => {
    it('bascule la langue exposée aux écrans', () => {
      const service = creerService();

      service.set('en');

      expect(service.current()).toBe('en');
      expect(service.isEnglish()).toBe(true);
    });

    it('mémorise le choix pour le prochain chargement de page', () => {
      creerService().set('en');

      expect(localStorage.getItem(STORAGE_KEY)).toBe('en');
      // Vérifié de bout en bout : écrire la bonne clé ne sert à rien si la
      // relecture ne la retrouve pas.
      expect(creerService().current()).toBe('en');
    });

    it('met à jour l\'attribut de langue du document', () => {
      const service = creerService();

      service.set('en');

      // `<html lang>` pilote la prononciation des lecteurs d'écran et la
      // césure. Le laisser sur `fr` avec un contenu anglais rend le parcours
      // incompréhensible en synthèse vocale.
      expect(document.documentElement.lang).toBe('en');

      service.set('fr');
      expect(document.documentElement.lang).toBe('fr');
    });

    it('bascule quand même la langue si le stockage refuse l\'écriture', () => {
      const service = creerService();
      vi.spyOn(localStorage, 'setItem').mockImplementation(() => {
        throw new Error('quota dépassé');
      });

      // En navigation privée, l'écriture lève. La mémorisation est un confort ;
      // le changement de langue, lui, est une demande explicite du client et
      // doit aboutir pour l'onglet en cours.
      expect(() => service.set('en')).not.toThrow();
      expect(service.current()).toBe('en');
      expect(document.documentElement.lang).toBe('en');
    });
  });

  describe('toggle', () => {
    it('passe du français à l\'anglais', () => {
      const service = creerService();
      service.set('fr');

      service.toggle();

      expect(service.current()).toBe('en');
    });

    it('passe de l\'anglais au français', () => {
      const service = creerService();
      service.set('en');

      service.toggle();

      expect(service.current()).toBe('fr');
    });

    it('revient à la langue de départ après deux basculements', () => {
      const service = creerService();
      service.set('fr');

      service.toggle();
      service.toggle();

      // Le bouton de bascule est le seul contrôle de langue de l'interface :
      // un aller-retour doit être neutre, sinon le client ne peut plus revenir.
      expect(service.current()).toBe('fr');
    });

    it('mémorise aussi le choix fait par bascule', () => {
      const service = creerService();
      service.set('fr');

      service.toggle();

      // La bascule passe par `set` : si quelqu'un la réécrivait en touchant
      // directement le signal, le choix ne survivrait plus au rechargement.
      expect(localStorage.getItem(STORAGE_KEY)).toBe('en');
      expect(document.documentElement.lang).toBe('en');
    });
  });

  describe('applyFromLink', () => {
    it('applique la langue portée par le lien WhatsApp', () => {
      const service = creerService();
      service.set('fr');

      service.applyFromLink('en');

      // Le bot connaît déjà la langue du client : le parcours doit s'ouvrir
      // dans celle-là, sans lui redemander.
      expect(service.current()).toBe('en');
      expect(localStorage.getItem(STORAGE_KEY)).toBe('en');
    });

    it('conserve la langue courante quand le lien n\'en porte aucune', () => {
      const service = creerService();
      service.set('en');

      service.applyFromLink(null);
      service.applyFromLink(undefined);
      service.applyFromLink('');

      // Entrée directe sans paramètre `lang` : ne rien changer vaut mieux que
      // repasser en français quelqu'un qui avait choisi l'anglais.
      expect(service.current()).toBe('en');
    });

    it('ignore une langue non gérée plutôt que de l\'appliquer', () => {
      const service = creerService();
      service.set('fr');

      service.applyFromLink('es');

      // La valeur vient du lien, donc de l'extérieur. L'écrire telle quelle
      // dans `document.lang` et dans le stockage laisserait une langue que rien
      // ne sait afficher.
      expect(service.current()).toBe('fr');
      expect(document.documentElement.lang).not.toBe('es');
    });

    it("n'accepte aujourd'hui qu'un code exactement en minuscules et sans région", () => {
      const service = creerService();
      service.set('fr');

      service.applyFromLink('EN');
      service.applyFromLink('en-US');

      // COMPORTEMENT ACTUEL, PAS SOUHAITABLE : la comparaison est stricte, donc
      // un lien portant `EN` ou `en-US` retombe silencieusement en français.
      // Ce qu'il faudrait : normaliser (minuscules, préfixe avant le tiret)
      // avant de comparer, comme le fait déjà la lecture de navigator.language.
      // Ce cas fige la limite pour qu'un élargissement futur soit un choix
      // visible et non un effet de bord.
      expect(service.current()).toBe('fr');
    });
  });

  describe('t', () => {
    it('rend le texte français tant que la langue est le français', () => {
      const service = creerService();
      service.set('fr');

      expect(service.t('Continuer', 'Continue')).toBe('Continuer');
    });

    it('rend le texte anglais après bascule', () => {
      const service = creerService();
      service.set('en');

      expect(service.t('Continuer', 'Continue')).toBe('Continue');
    });

    it('suit les changements de langue sans être figé à la première lecture', () => {
      const service = creerService();
      service.set('fr');

      expect(service.t('Continuer', 'Continue')).toBe('Continuer');
      service.toggle();
      expect(service.t('Continuer', 'Continue')).toBe('Continue');
    });

    it('rend une chaîne vide quand la traduction anglaise manque', () => {
      const service = creerService();
      service.set('en');

      // COMPORTEMENT ACTUEL, PAS SOUHAITABLE : une traduction laissée vide
      // produit un libellé vide à l'écran, donc un bouton sans texte, plutôt
      // que le texte français en repli. Le repli sur le français serait
      // dégradé mais utilisable ; une chaîne vide ne l'est pas.
      // Ce qu'il faudrait : `return this.lang() === 'en' ? (en || fr) : fr;`
      expect(service.t('Continuer', '')).toBe('');
    });
  });
});
