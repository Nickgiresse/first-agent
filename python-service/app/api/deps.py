from fastapi import Header, HTTPException, status

from app.config.settings import get_settings


def verify_internal_api_key(x_internal_api_key: str = Header(...)) -> None:
    """N'autorise que les appels portant la clé partagée avec Spring Boot.

    Ce service ne doit jamais être exposé publiquement : Angular ne l'appelle jamais
    directement, seul le backend Spring Boot y accède (réseau interne).
    """
    settings = get_settings()
    if x_internal_api_key != settings.internal_api_key:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Clé API interne invalide")
