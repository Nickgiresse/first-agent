import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';

import { ForgotPin } from './forgot-pin';
import { PinService } from '../../../core/services/pin';

/**
 * Demande de réinitialisation du code PIN.
 *
 * L'écran est ouvert à tous, sans session ni authentification : il suffit d'un
 * numéro de compte pour déclencher une procédure sur un compte réel. Ce qui le
 * tient tient à deux choses : la saisie doit être exactement un numéro de compte
 * complet avant qu'un seul appel ne parte, et le client doit pouvoir recommencer
 * quand la demande échoue.
 *
 * Les cas ci-dessous verrouillent la validation et les chemins d'erreur, et
 * documentent le fait que la seconde règle n'est aujourd'hui pas tenue.
 */
describe('ForgotPin', () => {
  let requestReset: ReturnType<typeof vi.fn>;

  // 23 chiffres : le préfixe d'agence « 10005 » suivi des 18 chiffres que le
  // client saisit à la vérification de compte.
  const COMPTE_VALIDE = '10005123456789012345678';
  const MESSAGE_PAR_DEFAUT = 'Impossible d’envoyer la demande. Réessayez.';

  beforeEach(() => {
    requestReset = vi.fn().mockReturnValue(
      of({ data: { emailSent: true, requiresBranchVisit: false, message: 'Instructions envoyées par e-mail.' } }),
    );

    TestBed.configureTestingModule({
      imports: [ForgotPin],
      providers: [{ provide: PinService, useValue: { requestReset } }],
    });
  });

  function ouvrirLEcran(): ComponentFixture<ForgotPin> {
    const fixture = TestBed.createComponent(ForgotPin);
    fixture.detectChanges();
    return fixture;
  }

  describe('validation du numéro de compte', () => {
    it('compte_vide_estInvalide', () => {
      const component = TestBed.createComponent(ForgotPin).componentInstance;

      expect(component.account.invalid).toBe(true);
    });

    it('compte_tropCourt_estInvalide', () => {
      // Un numéro tronqué désigne un autre compte ou aucun. Dans le premier cas,
      // la procédure de réinitialisation partirait sur le compte d'un tiers.
      const component = TestBed.createComponent(ForgotPin).componentInstance;

      component.account.setValue(COMPTE_VALIDE.slice(0, 22));

      expect(component.account.invalid).toBe(true);
    });

    it('compte_tropLong_estInvalide', () => {
      const component = TestBed.createComponent(ForgotPin).componentInstance;

      component.account.setValue(COMPTE_VALIDE + '9');

      expect(component.account.invalid).toBe(true);
    });

    it('compte_avecDesLettres_estInvalide', () => {
      // Le numéro de compte de la banque est purement numérique : une saisie
      // alphanumérique vient d'un autre format, souvent un IBAN recopié.
      const component = TestBed.createComponent(ForgotPin).componentInstance;

      component.account.setValue('FR761000512345678901234');

      expect(component.account.invalid).toBe(true);
    });

    it('compte_vingtTroisChiffres_estValide', () => {
      const component = TestBed.createComponent(ForgotPin).componentInstance;

      component.account.setValue(COMPTE_VALIDE);

      expect(component.account.valid).toBe(true);
    });

    it("compte_recopieAvecSesEspaces_estRefuseSansExplication", () => {
      // DÉFAUT DE PRODUCTION, comportement documenté tel qu'il est aujourd'hui.
      //
      // Le numéro de compte est presque toujours lu sur un relevé ou un RIB, où
      // il est écrit par groupes séparés par des espaces. Ce champ est un
      // `<input>` brut, sans aucune normalisation : la valeur collée garde ses
      // espaces, le motif la rejette, et le seul message affiché est « Le numéro
      // doit contenir 23 chiffres » alors que le client en a bien saisi 23.
      //
      // Le parcours d'onboarding, lui, dispose d'un champ dédié
      // (`AccountNumberInput`) qui retire les séparateurs et reformate la
      // saisie. Cet écran ne le réutilise pas.
      //
      // CE QUI DEVRAIT ÊTRE : les séparateurs doivent être retirés avant
      // validation, comme le fait déjà le champ partagé du parcours.
      const component = TestBed.createComponent(ForgotPin).componentInstance;

      component.account.setValue('10005 12345678901 2345678');

      expect(component.account.invalid).toBe(true);
    });
  });

  describe('envoi de la demande', () => {
    it("submit_saisieInvalide_nOuvreAucuneProcedure", () => {
      // Chaque demande déclenche un envoi d'e-mail ou une convocation en agence
      // sur un compte réel. Un numéro incomplet ne doit jamais en atteindre un.
      const component = TestBed.createComponent(ForgotPin).componentInstance;
      component.account.setValue('123');

      component.submit();

      expect(requestReset).not.toHaveBeenCalled();
    });

    it('submit_saisieVide_nOuvreAucuneProcedure', () => {
      const component = TestBed.createComponent(ForgotPin).componentInstance;

      component.submit();

      expect(requestReset).not.toHaveBeenCalled();
    });

    it('submit_saisieValide_transmetLeNumeroSaisi', () => {
      const component = TestBed.createComponent(ForgotPin).componentInstance;
      component.account.setValue(COMPTE_VALIDE);

      component.submit();

      expect(requestReset).toHaveBeenCalledWith(COMPTE_VALIDE);
    });

    it('submit_succes_afficheLeMessageDuServeur', () => {
      // Le serveur décide de la suite : e-mail envoyé, ou passage en agence
      // obligatoire. Substituer un texte fixe cacherait au client la démarche
      // qu'il doit réellement accomplir.
      requestReset.mockReturnValue(
        of({ data: { emailSent: false, requiresBranchVisit: true, message: 'Présentez-vous en agence.' } }),
      );
      const component = TestBed.createComponent(ForgotPin).componentInstance;
      component.account.setValue(COMPTE_VALIDE);

      component.submit();

      expect(component.submitted()).toBe(true);
      expect(component.message()).toBe('Présentez-vous en agence.');
    });

    it("submit_succes_retireLeBoutonPourEviterUnSecondEnvoi", () => {
      // Deux demandes engendrent deux jetons de réinitialisation : le client
      // suit le lien du premier e-mail reçu, qui n'est plus le jeton valide.
      const fixture = ouvrirLEcran();
      fixture.componentInstance.account.setValue(COMPTE_VALIDE);

      fixture.componentInstance.submit();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('button.primary-button')).toBeNull();
      expect(fixture.nativeElement.querySelector('.notice').textContent).toContain(
        'Instructions envoyées par e-mail.',
      );
    });
  });

  describe('chemins d’erreur', () => {
    it('submit_echec_afficheLeMessageMetier', () => {
      requestReset.mockReturnValue(
        throwError(() => ({ error: { message: 'Aucun compte ne correspond à ce numéro.' } })),
      );
      const component = TestBed.createComponent(ForgotPin).componentInstance;
      component.account.setValue(COMPTE_VALIDE);

      component.submit();

      // L'échec s'écrit dans `error()` et non dans `message()` : le second est
      // réservé au succès, et c'est lui qui referme l'écran.
      expect(component.error()).toBe('Aucun compte ne correspond à ce numéro.');
      expect(component.message()).toBeNull();
    });

    it('submit_echecSansMessage_afficheUnLibelleParDefaut', () => {
      // Sans repli, le cadre s'afficherait vide et le client ne saurait ni si sa
      // demande est partie, ni s'il doit recommencer.
      requestReset.mockReturnValue(throwError(() => ({})));
      const component = TestBed.createComponent(ForgotPin).componentInstance;
      component.account.setValue(COMPTE_VALIDE);

      component.submit();

      expect(component.error()).toBe(MESSAGE_PAR_DEFAUT);
    });

    it("submit_echec_neMarquePasLaDemandeCommeEnvoyee", () => {
      // Une panne ne doit pas laisser croire à l'écran que la procédure est
      // ouverte : c'est ce drapeau qui doit décider de la suite.
      requestReset.mockReturnValue(throwError(() => ({ error: { message: 'Service indisponible' } })));
      const component = TestBed.createComponent(ForgotPin).componentInstance;
      component.account.setValue(COMPTE_VALIDE);

      component.submit();

      expect(component.submitted()).toBe(false);
    });

    it("submit_echec_conserveLeBoutonPourUnNouvelEssai", () => {
      // Le gabarit arbitrait entre bouton et message sur le seul contenu de
      // `message()`, où le chemin d'erreur écrivait lui aussi : une panne
      // réseau passagère faisait donc disparaître le bouton d'envoi exactement
      // comme un succès. Le client lisait « Service indisponible » et n'avait
      // plus aucun moyen de réessayer, sur l'unique action de l'écran, alors
      // qu'il est déjà dans la situation d'avoir perdu son code PIN.
      requestReset.mockReturnValue(throwError(() => ({ error: { message: 'Service indisponible' } })));
      const fixture = ouvrirLEcran();
      fixture.componentInstance.account.setValue(COMPTE_VALIDE);

      fixture.componentInstance.submit();
      fixture.detectChanges();

      expect(fixture.componentInstance.submitted()).toBe(false);
      expect(fixture.nativeElement.querySelector('button.primary-button')).not.toBeNull();
      expect(fixture.nativeElement.textContent).toContain('Service indisponible');
    });

    it("submit_succesSansMessage_confirmeQuandMemeAuClient", () => {
      // Revers du même choix : quand le serveur répondait sans texte, l'écran
      // continuait d'afficher le bouton et rien n'indiquait que la demande
      // était partie. Le client, persuadé que son clic n'avait rien fait,
      // recommençait et ouvrait autant de procédures de réinitialisation.
      requestReset.mockReturnValue(
        of({ data: { emailSent: true, requiresBranchVisit: false, message: '' } }),
      );
      const fixture = ouvrirLEcran();
      fixture.componentInstance.account.setValue(COMPTE_VALIDE);

      fixture.componentInstance.submit();
      fixture.detectChanges();

      expect(fixture.componentInstance.submitted()).toBe(true);
      expect(fixture.nativeElement.querySelector('.notice')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('button.primary-button')).toBeNull();
    });

    it("submit_demandeEncoreEnCours_nEnEnvoiePasUneSeconde", () => {
      // Sans verrou, chaque clic partait vers le serveur tant que la réponse
      // n'était pas arrivée. Sur une connexion lente, celle-là même qui pousse
      // à recliquer, le client ouvrait autant de procédures qu'il avait de
      // clics et invalidait le jeton de chacune par la suivante.
      const enAttente = new Subject();
      requestReset.mockReturnValue(enAttente);
      const component = TestBed.createComponent(ForgotPin).componentInstance;
      component.account.setValue(COMPTE_VALIDE);

      component.submit();
      component.submit();
      component.submit();

      expect(requestReset).toHaveBeenCalledTimes(1);
    });
  });
});
