from app.config.settings import Settings
from app.face.detector import FaceKeypoint
from app.face.quality import _eyes_plausible, _mouth_plausible, _nose_plausible


def _frontal_keypoints() -> list[FaceKeypoint]:
    # Ordre : œil gauche, œil droit, nez, bouche, tragus gauche, tragus droit.
    # Visage bien de face, centré sur (0.5, 0.5), yeux alignés horizontalement.
    return [
        FaceKeypoint(x=0.40, y=0.45),  # œil gauche
        FaceKeypoint(x=0.60, y=0.45),  # œil droit
        FaceKeypoint(x=0.50, y=0.55),  # nez
        FaceKeypoint(x=0.50, y=0.65),  # bouche
        FaceKeypoint(x=0.30, y=0.48),  # tragus gauche
        FaceKeypoint(x=0.70, y=0.48),  # tragus droit
    ]


def test_eyes_plausible_for_frontal_face():
    settings = Settings()
    assert _eyes_plausible(_frontal_keypoints(), face_width_normalized=0.4, settings=settings) is True


def test_eyes_not_plausible_when_too_close_together():
    settings = Settings()
    keypoints = _frontal_keypoints()
    keypoints[0] = FaceKeypoint(x=0.495, y=0.45)
    keypoints[1] = FaceKeypoint(x=0.505, y=0.45)  # quasi confondus : profil ou détection dégénérée
    assert _eyes_plausible(keypoints, face_width_normalized=0.4, settings=settings) is False


def test_eyes_not_plausible_when_vertical_misalignment_exceeds_horizontal_distance():
    settings = Settings()
    keypoints = _frontal_keypoints()
    keypoints[0] = FaceKeypoint(x=0.49, y=0.20)  # décalage vertical énorme vs écart horizontal
    keypoints[1] = FaceKeypoint(x=0.51, y=0.60)
    assert _eyes_plausible(keypoints, face_width_normalized=0.4, settings=settings) is False


def test_nose_plausible_when_below_eye_line():
    assert _nose_plausible(_frontal_keypoints()) is True


def test_nose_not_plausible_when_above_eye_line():
    keypoints = _frontal_keypoints()
    keypoints[2] = FaceKeypoint(x=0.50, y=0.10)  # nez anormalement au-dessus des yeux
    assert _nose_plausible(keypoints) is False


def test_mouth_plausible_when_below_nose():
    assert _mouth_plausible(_frontal_keypoints()) is True


def test_mouth_not_plausible_when_above_nose():
    keypoints = _frontal_keypoints()
    keypoints[3] = FaceKeypoint(x=0.50, y=0.10)  # bouche anormalement au-dessus du nez
    assert _mouth_plausible(keypoints) is False


def test_plausibility_checks_return_false_with_insufficient_keypoints():
    settings = Settings()
    partial = [FaceKeypoint(x=0.4, y=0.4)]
    assert _eyes_plausible(partial, face_width_normalized=0.4, settings=settings) is False
    assert _nose_plausible(partial) is False
    assert _mouth_plausible(partial) is False
