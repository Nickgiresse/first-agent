from app.config.settings import Settings
from app.liveness.landmarker import FrameAnalysis

MIN_DETECTED_FRAMES = 2  # il faut au moins un point de référence + une frame montrant l'action


def verify_action(action: str, frames: list[FrameAnalysis], settings: Settings) -> bool:
    detected = [f for f in frames if f.faceDetected]
    if len(detected) < MIN_DETECTED_FRAMES:
        return False

    if action == "BLINK":
        return _verify_blink(detected, settings)
    if action == "SMILE":
        return _verify_smile(detected, settings)
    if action in ("TURN_LEFT", "TURN_RIGHT"):
        return _verify_head_turn(action, detected, settings)
    if action in ("LOOK_UP", "LOOK_DOWN"):
        return _verify_head_pitch(action, detected, settings)
    return False


def _verify_blink(frames: list[FrameAnalysis], settings: Settings) -> bool:
    """Exige une vraie transition ouvert -> fermé -> ouvert (pas juste une frame les yeux fermés) :
    une photo statique, imprimée ou rejouée, ne peut pas produire cette transition."""
    scores = [_blink_score(f) for f in frames]
    saw_open_before_close = False
    saw_close = False
    for score in scores:
        if score < settings.blink_score_open and not saw_close:
            saw_open_before_close = True
        elif score > settings.blink_score_closed and saw_open_before_close:
            saw_close = True
        elif score < settings.blink_score_open and saw_close:
            return True
    return False


def _blink_score(frame: FrameAnalysis) -> float:
    left = frame.blendshapes.get("eyeBlinkLeft", 0.0)
    right = frame.blendshapes.get("eyeBlinkRight", 0.0)
    return (left + right) / 2


def _verify_smile(frames: list[FrameAnalysis], settings: Settings) -> bool:
    return any(_smile_score(f) >= settings.smile_score_threshold for f in frames)


def _smile_score(frame: FrameAnalysis) -> float:
    left = frame.blendshapes.get("mouthSmileLeft", 0.0)
    right = frame.blendshapes.get("mouthSmileRight", 0.0)
    return (left + right) / 2


def _verify_head_turn(action: str, frames: list[FrameAnalysis], settings: Settings) -> bool:
    """Compare chaque frame au lacet (yaw) de la première frame, prise comme référence neutre,
    plutôt qu'à un angle absolu : reste correct quelle que soit la position de départ de la
    personne face à la caméra.

    Convention de signe (delta_yaw < 0 = droite, > 0 = gauche) : la décomposition de matrice de
    rotation standard supposait l'inverse (delta < 0 = gauche), mais un test réel avec caméra
    frontale (2026-07-31) a montré que le résultat était inversé par rapport à la consigne
    affichée à l'utilisateur — corrigé ici en conséquence, comme anticipé dans le commentaire
    d'origine ("inverser si les tests montrent l'inverse").
    """
    baseline_yaw = frames[0].yawDegrees
    deltas = [f.yawDegrees - baseline_yaw for f in frames[1:]]
    if action == "TURN_LEFT":
        return any(delta >= settings.head_turn_threshold_degrees for delta in deltas)
    return any(delta <= -settings.head_turn_threshold_degrees for delta in deltas)


def _verify_head_pitch(action: str, frames: list[FrameAnalysis], settings: Settings) -> bool:
    """Même principe que _verify_head_turn, appliqué au tangage (pitch) pour lever/baisser la tête.

    Convention de signe inversée par rapport à l'hypothèse initiale, pour la même raison que
    _verify_head_turn (confirmé sur un vrai test caméra le 2026-07-31)."""
    baseline_pitch = frames[0].pitchDegrees
    deltas = [f.pitchDegrees - baseline_pitch for f in frames[1:]]
    if action == "LOOK_UP":
        return any(delta <= -settings.head_pitch_threshold_degrees for delta in deltas)
    return any(delta >= settings.head_pitch_threshold_degrees for delta in deltas)


def score_for_action(action: str, frames: list[FrameAnalysis]) -> list[float]:
    """Scores bruts pertinents pour cette action (diagnostic/logs uniquement, n'affecte pas la décision)."""
    detected = [f for f in frames if f.faceDetected]
    if not detected:
        return []
    if action == "BLINK":
        return [_blink_score(f) for f in detected]
    if action == "SMILE":
        return [_smile_score(f) for f in detected]
    if action in ("TURN_LEFT", "TURN_RIGHT"):
        baseline = detected[0].yawDegrees
        return [f.yawDegrees - baseline for f in detected]
    if action in ("LOOK_UP", "LOOK_DOWN"):
        baseline = detected[0].pitchDegrees
        return [f.pitchDegrees - baseline for f in detected]
    return []
