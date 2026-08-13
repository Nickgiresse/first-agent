class SessionNotFoundError(ValueError):
    pass


class SessionExpiredError(ValueError):
    pass


class ActionMismatchError(ValueError):
    pass


class FaceChangedError(ValueError):
    """Le visage présenté a changé en cours de défi.

    Deux personnes se relaient devant la caméra : l'une joue les actions, l'autre
    est présentée pour la comparaison. Sans cette détection, le défi valide la
    vivacité de la première et l'identité de la seconde.

    La session est détruite plutôt que marquée en échec : la reprendre
    reviendrait à laisser le fraudeur conserver les actions déjà validées.
    """
