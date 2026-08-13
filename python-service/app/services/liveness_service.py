from app.config.settings import Settings, get_settings
from app.liveness.actions import score_for_action, verify_action
from app.liveness.challenge import generate_challenge
from app.liveness.errors import (
    ActionMismatchError,
    FaceChangedError,
    SessionExpiredError,
    SessionNotFoundError,
)
from app.liveness.landmarker import analyze_frame
from app.liveness.session_store import LivenessSession, create_session, drop_session, get_session
from app.models.liveness_models import ChallengeStartResponse, ChallengeStatusResponse, ChallengeVerifyResponse
from app.utils.image_io import decode_image
from app.verification.comparison import cosine_similarity

# Nombre de frames dont on calcule l'empreinte par rafale.
#
# Le calcul ArcFace est la partie coûteuse de la vérification (inférence ONNX sur chaque
# visage aligné) et une rafale en compte plusieurs dizaines. Deux suffisent à rattacher la
# rafale à une personne. Le risque résiduel est assumé et documenté : intercaler un autre
# visage au MILIEU d'une rafale reste théoriquement possible, mais il faudrait que le tirage
# papier substitué cligne des yeux ou sourie pour que l'action soit validée.
_FRAMES_EMPREINTE = 2


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

    # Rattachement du défi à une personne. Fait AVANT d'enregistrer l'action comme validée :
    # une rafale jouée par un autre visage ne doit pas faire progresser le défi.
    _lier_le_visage(session, frames_bytes, frames, settings)

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


def _lier_le_visage(
    session: LivenessSession, frames_bytes: list[bytes], frames: list, settings: Settings
) -> None:
    """Mémorise le visage du défi, ou vérifie qu'il n'a pas changé.

    Première rafale exploitable : on retient l'empreinte. Rafales suivantes : on compare. Un
    écart signifie que deux personnes se relaient devant la caméra, l'une jouant les actions
    et l'autre étant destinée à la comparaison faciale. La session est alors détruite.

    Silencieux si aucune empreinte n'est calculable (visage non détecté, alignement impossible) :
    l'absence de preuve n'est pas une preuve de fraude, et le dossier sera de toute façon traité
    comme non lié au moment de la comparaison.
    """
    empreintes = _empreintes_de_la_rafale(frames_bytes, frames)
    if not empreintes:
        return

    if session.embedding is None:
        session.embedding = empreintes[0]
        return

    seuil = settings.liveness_binding_similarity_threshold
    for empreinte in empreintes:
        if cosine_similarity(session.embedding, empreinte) < seuil:
            drop_session(session.session_id)
            raise FaceChangedError(
                "Le visage présenté a changé pendant le défi. Reprenez le parcours depuis "
                "le début, une seule personne devant la caméra."
            )


def _empreintes_de_la_rafale(frames_bytes: list[bytes], frames: list) -> list:
    empreintes = []
    # Import local : verification_service importe app.liveness.landmarker, le faire en tête
    # de module créerait un cycle d'import entre les deux services.
    from app.services.verification_service import embed_face

    for raw, frame in zip(frames_bytes, frames):
        if len(empreintes) >= _FRAMES_EMPREINTE:
            break
        if not frame.faceDetected:
            continue
        try:
            empreintes.append(embed_face(raw, frame))
        except (ValueError, RuntimeError) as exc:
            # Alignement ou inférence impossible sur cette frame : on passe à la suivante
            # plutôt que d'interrompre un défi par ailleurs valable.
            print(f"[liveness] empreinte non calculable sur une frame : {exc}")
    return empreintes


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
