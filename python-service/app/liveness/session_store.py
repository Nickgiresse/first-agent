import time
import uuid
from dataclasses import dataclass, field

# Stockage en mémoire, suffisant pour une seule instance du service. À remplacer par Redis
# (ou équivalent partagé) si le service est un jour déployé en plusieurs instances derrière
# un load balancer, pour que toutes les instances voient les mêmes sessions.
_SESSIONS: dict[str, "LivenessSession"] = {}


@dataclass
class LivenessSession:
    session_id: str
    actions: list[str]
    ttl_seconds: int
    completed_actions: list[str] = field(default_factory=list)
    created_at: float = field(default_factory=time.time)

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


def _cleanup_expired() -> None:
    expired_ids = [sid for sid, session in _SESSIONS.items() if session.expired]
    for sid in expired_ids:
        del _SESSIONS[sid]
