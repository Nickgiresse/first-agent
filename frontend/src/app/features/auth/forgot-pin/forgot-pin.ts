import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { errorMessage } from '../../../core/utils/error-message';
import { PinService } from '../../../core/services/pin';

@Component({
  selector: 'afb-forgot-pin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  templateUrl: './forgot-pin.html',
  styleUrl: './forgot-pin.scss',
})
export class ForgotPin {
  private readonly pin = inject(PinService);

  readonly account = new FormControl('', {
    nonNullable: true,
    validators: [Validators.pattern(/^\d{23}$/), Validators.required],
  });

  /** Demande aboutie. Distinct de `error` : seul le succès referme l'écran. */
  readonly submitted = signal(false);

  /** Message du serveur en cas de succès : courriel envoyé, ou passage en agence. */
  readonly message = signal<string | null>(null);

  /**
   * Échec, affiché SANS masquer le bouton d'envoi.
   *
   * <p>Le gabarit arbitrait auparavant entre bouton et message sur la seule
   * présence d'un texte, si bien qu'une panne réseau faisait disparaître le
   * bouton d'envoi. Sur l'unique action de cet écran, le client n'avait plus
   * aucun moyen de réessayer, et rien ne lui disait pourquoi.
   */
  readonly error = signal<string | null>(null);

  readonly submitting = signal(false);

  /**
   * Ne conserve que les chiffres de la saisie.
   *
   * <p>Un numéro recopié depuis un relevé arrive avec ses espaces. Sans cela,
   * il était refusé par « Le numéro doit contenir 23 chiffres » alors qu'il en
   * contient bien 23, ce qui est le plus décourageant des messages.
   */
  onAccountInput(): void {
    const chiffres = this.account.value.replace(/\D/g, '').slice(0, 23);
    if (chiffres !== this.account.value) {
      this.account.setValue(chiffres);
    }
    this.error.set(null);
  }

  submit(): void {
    if (this.account.invalid) {
      // Sans cela, le bouton restait inerte sans que le message d'erreur du
      // champ n'apparaisse, celui-ci étant conditionné à `touched`.
      this.account.markAsTouched();
      return;
    }
    // La garde sur l'envoi en cours n'est pas un doublon de l'attribut
    // `disabled` : trois clics rapides ouvraient trois procédures de
    // réinitialisation, donc trois courriels et trois jetons valides.
    if (this.submitting()) {
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    this.pin.requestReset(this.account.value).subscribe({
      next: (r) => {
        this.submitting.set(false);
        this.submitted.set(true);
        // Le serveur peut aboutir sans texte ; l'écran doit tout de même
        // basculer, faute de quoi le client recliquerait indéfiniment.
        this.message.set(r?.data?.message ?? 'Votre demande a bien été enregistrée.');
      },
      error: (e) => {
        this.submitting.set(false);
        this.error.set(errorMessage(e, 'Impossible d’envoyer la demande. Réessayez.'));
      },
    });
  }
}
