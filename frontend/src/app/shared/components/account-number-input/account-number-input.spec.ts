import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AccountNumberInput } from './account-number-input';

/**
 * Le composant est en `OnPush`, ce qui l'expose à un piège précis.
 *
 * Un composant en `OnPush` n'est réexaminé que sur un de ses propres
 * événements, un changement d'entrée ou un signal. L'API de formulaires, elle,
 * appelle `writeValue` et `setDisabledState` en dehors de tout cela : sans
 * rappel explicite, la vue reste sur l'état précédent.
 *
 * Rien n'échoue alors. Le champ resterait simplement saisissable à l'écran
 * alors que le formulaire le considère désactivé, et le défaut ne se verrait
 * qu'à l'usage. Ces cas existent pour que la stratégie de détection ne puisse
 * pas être appliquée à ce composant sans son garde-fou.
 */
describe('AccountNumberInput', () => {
  let fixture: ComponentFixture<AccountNumberInput>;
  let component: AccountNumberInput;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AccountNumberInput],
    }).compileComponents();

    fixture = TestBed.createComponent(AccountNumberInput);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function input(): HTMLInputElement {
    return fixture.nativeElement.querySelector('input');
  }

  it('affiche la valeur écrite par le formulaire, malgré OnPush', () => {
    component.writeValue('1000500001000000758278');

    fixture.detectChanges();

    // Le formatage par groupes est le rôle du composant ; ce qui est vérifié
    // ici est que la valeur parvient jusqu'à l'écran.
    expect(input().value).toContain('10005');
  });

  it('désactive réellement le champ quand le formulaire le demande', () => {
    component.setDisabledState(true);

    fixture.detectChanges();

    // Sans markForCheck dans setDisabledState, cette assertion échoue : le
    // champ reste saisissable alors que le formulaire le croit désactivé.
    expect(input().disabled).toBe(true);
  });

  it('réactive le champ quand le formulaire le demande', () => {
    component.setDisabledState(true);
    fixture.detectChanges();

    component.setDisabledState(false);
    fixture.detectChanges();

    expect(input().disabled).toBe(false);
  });

  it('ne retient que les chiffres de la valeur reçue', () => {
    component.writeValue('10005-abc-00001');

    expect(component.rawDigits).toBe('1000500001');
  });

  describe('formatage du RIB', () => {
    // Le RIB Afriland se lit en quatre blocs : code banque, code guichet,
    // numéro de compte, clé. Ce composant saisit les trois derniers, le code
    // banque étant affiché à côté et non modifiable. Un regroupement erroné
    // ferait relire un numéro pour un autre au moment de la vérification.

    it('groupe en 5, 11 puis 2 chiffres', () => {
      expect(component.format('00001000000758278 1'.replace(/\D/g, ''))).toBe(
        '00001 00000075827 81',
      );
    });

    it('ne groupe que ce qui est saisi, sans combler', () => {
      expect(component.format('')).toBe('');
      expect(component.format('1')).toBe('1');
      expect(component.format('00001')).toBe('00001');
      expect(component.format('000012')).toBe('00001 2');
    });

    it('ignore tout ce qui n\'est pas un chiffre', () => {
      expect(component.format('00001 abc 00000075827-81')).toBe('00001 00000075827 81');
    });

    it('ne dépasse jamais 18 chiffres', () => {
      // Au-delà, la saisie déborderait sur des positions qui n'existent pas
      // dans un RIB : la troncature est une protection, pas un confort.
      const trop = '9'.repeat(30);

      expect(component.format(trop).replace(/\D/g, '')).toHaveLength(18);
    });

    it('conserve les zéros de tête, qui portent du sens', () => {
      // Un code guichet « 00001 » n'est pas « 1 ». Toute conversion numérique
      // intermédiaire les perdrait.
      expect(component.format('00001000000758278')).toMatch(/^00001 /);
    });
  });

  describe('valeur émise vers le formulaire', () => {
    it('transmet les chiffres bruts, sans les séparateurs affichés', () => {
      let recu: string | null = null;
      component.registerOnChange(v => (recu = v));

      const champ = input();
      champ.value = '00001 00000075827 81';
      champ.dispatchEvent(new Event('input'));

      // Le formatage est un confort de lecture ; ce qui part vers le backend
      // ne doit contenir que des chiffres.
      expect(recu).toBe('000010000007582781');
    });
  });
});
