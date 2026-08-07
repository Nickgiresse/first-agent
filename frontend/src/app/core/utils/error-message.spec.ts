import { errorMessage } from './error-message';

/**
 * Cet extracteur remplace les `(e as any)?.error?.message` du parcours.
 *
 * Son intérêt n'est pas d'être joli mais d'être vérifiable : le transtypage
 * qu'il remplace échouait silencieusement, rendant `undefined` et faisant
 * afficher le message de repli sans que personne ne sache que le vrai message
 * avait été perdu.
 */
describe('errorMessage', () => {
  const REPLI = 'Une erreur est survenue.';

  it("préfère le message du serveur, qui porte la raison métier", () => {
    const erreurHttp = {
      status: 409,
      error: { message: "L'identité ne correspond pas au titulaire du compte." },
      message: 'Http failure response for /api/v1/ocr: 409 Conflict',
    };

    // Le message technique d'Angular est inutilisable par un client ; celui du
    // serveur lui dit quoi faire.
    expect(errorMessage(erreurHttp, REPLI)).toBe(
      "L'identité ne correspond pas au titulaire du compte.",
    );
  });

  it('retombe sur le message propre quand le serveur n\'en fournit pas', () => {
    expect(errorMessage(new Error('Réseau indisponible'), REPLI)).toBe('Réseau indisponible');
  });

  it('retombe sur le repli quand rien n\'est exploitable', () => {
    expect(errorMessage(null, REPLI)).toBe(REPLI);
    expect(errorMessage(undefined, REPLI)).toBe(REPLI);
    expect(errorMessage('une chaîne', REPLI)).toBe(REPLI);
    expect(errorMessage(42, REPLI)).toBe(REPLI);
    expect(errorMessage({}, REPLI)).toBe(REPLI);
  });

  it('ignore un message vide plutôt que de l\'afficher', () => {
    // Un message vide passerait un simple test de présence et donnerait un
    // encart d'erreur sans texte, ce qui est pire que le repli.
    expect(errorMessage({ error: { message: '' }, message: 'de secours' }, REPLI)).toBe(
      'de secours',
    );
    expect(errorMessage({ message: '' }, REPLI)).toBe(REPLI);
  });

  it('ignore un message qui n\'est pas une chaîne', () => {
    // Un corps d'erreur mal formé ne doit pas se retrouver affiché sous la
    // forme « [object Object] ».
    expect(errorMessage({ error: { message: { code: 42 } } }, REPLI)).toBe(REPLI);
    expect(errorMessage({ message: ['a', 'b'] }, REPLI)).toBe(REPLI);
  });

  it('tolère un corps d\'erreur qui n\'est pas un objet', () => {
    expect(errorMessage({ error: 'texte brut', message: 'de secours' }, REPLI)).toBe('de secours');
    expect(errorMessage({ error: null, message: 'de secours' }, REPLI)).toBe('de secours');
  });
});
