class SessionNotFoundError(ValueError):
    pass


class SessionExpiredError(ValueError):
    pass


class ActionMismatchError(ValueError):
    pass
