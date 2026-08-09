/**
 * Aucune session de parcours n'est ouverte.
 *
 * <p>Les appels du parcours partaient auparavant avec un en-tête de session
 * vide, et c'est le serveur qui les refusait. Le client attendait un
 * aller-retour réseau pour recevoir un 401 générique, et rien dans le
 * diagnostic ne disait que la session manquait côté navigateur.
 *
 * <p>Le garde d'onboarding rend ce cas improbable ; il n'est pas impossible
 * pour autant, une session pouvant expirer entre l'activation de la route et
 * l'appel. S'il survient, mieux vaut qu'il se nomme.
 *
 * <p>L'erreur est portée par le flux, et non levée de façon synchrone : les
 * appelants la reçoivent dans leur gestionnaire d'erreur, comme n'importe quel
 * échec réseau, sans qu'aucun d'eux ait à changer.
 */
export class SessionAbsenteError extends Error {
  constructor() {
    super('Votre session a expiré. Reprenez la vérification de votre compte.');
    this.name = 'SessionAbsenteError';
  }

  /**
   * Forme attendue par l'extracteur de message du projet.
   *
   * <p>`core/utils/error-message.ts` cherche d'abord `error.message`, la forme
   * d'une réponse HTTP. La respecter évite que ce cas affiche le libellé de
   * repli au lieu de sa propre explication.
   */
  readonly error = { message: 'Votre session a expiré. Reprenez la vérification de votre compte.' };
}
