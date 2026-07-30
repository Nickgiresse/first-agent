from app.config.settings import Settings, get_settings
from app.liveness.actions import score_for_action, verify_action
from app.liveness.challenge import generate_challenge
from app.liveness.errors import ActionMismatchError, SessionExpiredError, SessionNotFoundError
from app.liveness.landmarker import analyze_frame
from app.liveness.session_store import LivenessSession, create_session, get_session
from app.models.liveness_models import ChallengeStartResponse, ChallengeStatusResponse, ChallengeVerifyResponse
from app.utils.image_io import decode_image


def start_challenge(settings: Settings | None = None) -> ChallengeStartResponse:
    settings = settings or get_settings()
    actions = generate_challenge(settings.liveness_challenge_action_count)
    session = create_session(actions, settings.liveness_session_ttl_seconds)
    return ChallengeStartResponse(
        sessionId=session.session_id,
        actions=session.actions,
        expiresInSeconds=settings.liveness_session_ttl_seconds,
    )


def verify_challenge(
    session_id: str, action: str, frames_bytes: list[bytes], settings: Settings | None = None
) -> ChallengeVerifyResponse:
    settings = settings or get_settings()
    session = _get_active_session(session_id)

    if session.current_action != action:
        raise ActionMismatchError(
            f"Action attendue : {session.current_action!r}, reçue : {action!r}. "
            "Les actions doivent être vérifiées dans l'ordre annoncé par /challenge/start."
        )

    frames = [analyze_frame(decode_image(raw)) for raw in frames_bytes]
    completed = verify_action(action, frames, settings)

    print(
        f"[liveness] action={action} completed={completed} "
        f"faces_detected={sum(1 for f in frames if f.faceDetected)}/{len(frames)} "
        f"scores={[round(s, 3) for s in score_for_action(action, frames)]}"
    )

    if completed and action not in session.completed_actions:
        session.completed_actions.append(action)

    return ChallengeVerifyResponse(
        sessionId=session.session_id,
        action=action,
        actionCompleted=completed,
        completedActions=list(session.completed_actions),
        remainingActions=_remaining_actions(session),
        allActionsCompleted=session.all_completed,
    )


def get_status(session_id: str) -> ChallengeStatusResponse:
    session = _get_active_session(session_id)
    decision = "LIVE" if session.all_completed else "IN_PROGRESS"
    return ChallengeStatusResponse(
        sessionId=session.session_id,
        actions=session.actions,
        completedActions=list(session.completed_actions),
        remainingActions=_remaining_actions(session),
        allActionsCompleted=session.all_completed,
        decision=decision,
    )


def _get_active_session(session_id: str) -> LivenessSession:
    session = get_session(session_id)
    if session is None:
        raise SessionNotFoundError(f"Session de vivacité introuvable ou expirée : {session_id}")
    if session.expired:
        raise SessionExpiredError(f"Session de vivacité expirée : {session_id}")
    return session


def _remaining_actions(session: LivenessSession) -> list[str]:
    return [action for action in session.actions if action not in session.completed_actions]
