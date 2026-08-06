/**
 * Extrait un message lisible d'une erreur de type inconnu.
 *
 * Les gestionnaires d'erreur écrivaient `(e as any)?.error?.message`. Le
 * transtypage en `any` fait plus que contourner un contrôle : il désactive le
 * typage sur toute la chaîne d'accès, si bien qu'une faute de frappe dans un
 * nom de propriété ne se voit ni à la compilation ni à l'exécution, et rend
 * simplement `undefined`. Le message de repli s'affiche alors sans que
 * personne ne sache que le vrai message a été perdu.
 *
 * Le parcours reçoit deux formes d'erreur : celles du serveur, où le message
 * utile est sous `error.message` de la réponse HTTP, et celles du navigateur
 * ou du code, où il est directement sur `message`.
 */
export function errorMessage(error: unknown, fallback: string): string {
  if (typeof error !== 'object' || error === null) {
    return fallback;
  }

  // Réponse HTTP : le corps rendu par le serveur porte le message métier, qui
  // est le plus utile au client.
  const body = (error as { error?: unknown }).error;
  if (typeof body === 'object' && body !== null) {
    const serverMessage = (body as { message?: unknown }).message;
    if (typeof serverMessage === 'string' && serverMessage.length > 0) {
      return serverMessage;
    }
  }

  const ownMessage = (error as { message?: unknown }).message;
  if (typeof ownMessage === 'string' && ownMessage.length > 0) {
    return ownMessage;
  }

  return fallback;
}
