import { Injectable, signal } from '@angular/core';

export type Lang = 'fr' | 'en';

const STORAGE_KEY = 'onboarding-lang';

/**
 * Langue de l'interface (français / anglais), exigée par les CGU et le cahier des charges
 * (parcours bilingue, langue mémorisée pour le bot et les reçus).
 *
 * Ordre de résolution : langue portée par le lien WhatsApp (?t= → champ `lang`) > choix mémorisé
 * du client > langue du navigateur > français par défaut.
 */
@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly lang = signal<Lang>(this.resolveInitial());

  readonly current = this.lang.asReadonly();

  isEnglish(): boolean {
    return this.lang() === 'en';
  }

  set(lang: Lang): void {
    this.lang.set(lang);
    try {
      localStorage.setItem(STORAGE_KEY, lang);
    } catch {
      // stockage indisponible (navigation privée) : la langue reste valable pour cet onglet
    }
    document.documentElement.lang = lang;
  }

  toggle(): void {
    this.set(this.lang() === 'fr' ? 'en' : 'fr');
  }

  /**
   * Applique la langue transmise par le lien d'onboarding, si elle est exploitable.
   *
   * La valeur est normalisée avant comparaison, comme l'est déjà celle du
   * navigateur : le lien est fabriqué par le bot WhatsApp, et rien ne garantit
   * qu'il envoie exactement {@code en}. Une comparaison stricte rejetait
   * {@code EN} comme {@code en-US}, et le client anglophone qui venait
   * précisément d'écrire en anglais au bot arrivait sur un parcours en
   * français.
   */
  applyFromLink(lang: string | null | undefined): void {
    const normalisee = lang?.toLowerCase().split('-')[0];
    if (normalisee === 'fr' || normalisee === 'en') {
      this.set(normalisee);
    }
  }

  /**
   * Traduction ponctuelle : t('Texte français', 'English text').
   *
   * Le français sert de repli quand la traduction anglaise manque. Un écran en
   * partie français est dégradé mais reste utilisable ; une chaîne vide donne
   * un bouton sans texte, sur lequel le client ne peut que deviner.
   */
  t(fr: string, en: string): string {
    return this.lang() === 'en' ? en || fr : fr;
  }

  private resolveInitial(): Lang {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored === 'fr' || stored === 'en') {
        return stored;
      }
    } catch {
      // ignore
    }
    return navigator?.language?.toLowerCase().startsWith('en') ? 'en' : 'fr';
  }
}
