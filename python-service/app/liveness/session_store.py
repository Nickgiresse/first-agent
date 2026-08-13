import time
import uuid
from dataclasses import dataclass, field
from typing import Any

# Stockage en mémoire, suffisant pour une seule instance du service. À remplacer par Redis
# (ou équivalent partagé) si le service est un jour déployé en plusieurs instances derrière
# un load balancer, pour que toutes les instances voient les mêmes sessions.
#
# Depuis la liaison visage-vivacité, une session porte une empreinte biométrique : elle ne
# doit JAMAIS être écrite sur disque ni journalisée. Elle vit ici, en mémoire du processus,
# et disparaît à l'expiration (5 minutes) ou au redémarrage.
_SESSIONS: dict[str, "LivenessSession"] = {}


@dataclass
class LivenessSession:
    session_id: str
    actions: list[str]
    ttl_seconds: int
    completed_actions: list[str] = field(default_factory=list)
    created_at: float = field(default_factory=time.time)
    # Empreinte ArcFace du visage qui a joué la première action vérifiée.
    #
    # C'est elle qui rattache le défi à une personne. Sans elle, « la vivacité est
    # prouvée » et « le selfie correspond à la pièce » sont deux faits vrais
    # séparément et qui peuvent concerner deux individus différents.
    #
    # Typée Any plutôt que np.ndarray pour ne pas imposer numpy à ce module, qui
    # est importé par des chemins qui n'en ont pas besoin.
    embedding: Any | None = None

    @property
    def expires_at(self) -> float:
        return self.created_at + self.ttl_seconds

    @property
    def expired(self) -> bool:
        return time.time() > self.expires_at

    @property
    def current_action(self) -> str | None:
        for action in self.actions:
            if action not in self.completed_actions:
                return action
        return None

    @property
    def all_completed(self) -> bool:
        return self.current_action is None


def create_session(actions: list[str], ttl_seconds: int) -> LivenessSession:
    _cleanup_expired()
    session = LivenessSession(session_id=str(uuid.uuid4()), actions=actions, ttl_seconds=ttl_seconds)
    _SESSIONS[session.session_id] = session
    return session


def get_session(session_id: str) -> LivenessSession | None:
    _cleanup_expired()
    return _SESSIONS.get(session_id)


def drop_session(session_id: str) -> None:
    """Détruit une session. Utilisé quand le visage change en cours de défi :
    la conserver laisserait au fraudeur le bénéfice des actions déjà validées."""
    _SESSIONS.pop(session_id, None)


def _cleanup_expired() -> None:
    expired_ids = [sid for sid, session in _SESSIONS.items() if session.expired]
    for sid in expired_ids:
        del _SESSIONS[sid]
