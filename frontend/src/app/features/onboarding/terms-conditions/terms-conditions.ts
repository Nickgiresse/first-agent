import { Location } from '@angular/common';
import { AfterViewInit, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { OnboardingService } from '../../../core/services/onboarding';

@Component({
  selector: 'app-terms-conditions',
  imports: [],
  templateUrl: './terms-conditions.html',
  styleUrl: './terms-conditions.scss'
})
export class TermsConditions implements AfterViewInit {
  private readonly service = inject(OnboardingService);
  private readonly router = inject(Router);
  private readonly location = inject(Location);

  readonly accepted = signal(false);
  readonly busy = signal(false);

  /** Le texte a-t-il été parcouru jusqu'au bout ? Conditionne la case d'acceptation. */
  readonly hasRead = signal(false);

  private readonly termsScroll = viewChild<ElementRef<HTMLElement>>('termsScroll');

  ngAfterViewInit(): void {
    // Un texte assez court pour tenir sans défilement ne peut pas être « fait
    // défiler jusqu'en bas » : exiger un défilement impossible bloquerait
    // définitivement le parcours. La lecture est alors réputée faite.
    this.checkRead();
  }

  onScroll(): void {
    this.checkRead();
  }

  private checkRead(): void {
    if (this.hasRead()) return;
    const zone = this.termsScroll()?.nativeElement;
    if (!zone) return;
    // Hauteur nulle = zone pas encore rendue ; conclure ici déverrouillerait
    // la case sans la moindre lecture.
    if (!zone.clientHeight) return;
    const tientSansDefilement = zone.scrollHeight <= zone.clientHeight + 8;
    const arriveEnBas = zone.scrollTop + zone.clientHeight >= zone.scrollHeight - 12;
    if (tientSansDefilement || arriveEnBas) this.hasRead.set(true);
  }

  goBack(): void {
    this.location.back();
  }

  submit(): void {
    // Double garde : le gabarit désactive déjà le bouton, mais la décision de
    // conformité ne doit pas reposer sur le seul affichage.
    if (!this.accepted() || !this.hasRead()) return;
    this.busy.set(true);
    this.service.acceptTerms({ termsAccepted: true }).subscribe({
      next: () => this.service.completeOnboarding().subscribe({
        next: () => this.router.navigateByUrl('/onboarding/success'),
        error: () => this.busy.set(false)
      }),
      error: () => this.busy.set(false)
    });
  }
}
