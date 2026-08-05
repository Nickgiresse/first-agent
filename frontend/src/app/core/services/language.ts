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

  /** Applique la langue transmise par le lien d'onboarding, si elle est exploitable. */
  applyFromLink(lang: string | null | undefined): void {
    if (lang === 'fr' || lang === 'en') {
      this.set(lang);
    }
  }

  /** Traduction ponctuelle : t('Texte français', 'English text'). */
  t(fr: string, en: string): string {
    return this.lang() === 'en' ? en : fr;
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
