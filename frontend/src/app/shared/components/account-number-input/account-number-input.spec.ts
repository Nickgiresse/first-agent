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
});
