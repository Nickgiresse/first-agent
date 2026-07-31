from app.config.settings import Settings
from app.liveness.actions import verify_action
from app.liveness.landmarker import FrameAnalysis


def _frame(face_detected=True, blink=0.0, smile=0.0, yaw=0.0, pitch=0.0) -> FrameAnalysis:
    return FrameAnalysis(
        faceDetected=face_detected,
        blendshapes={
            "eyeBlinkLeft": blink,
            "eyeBlinkRight": blink,
            "mouthSmileLeft": smile,
            "mouthSmileRight": smile,
        },
        yawDegrees=yaw,
        pitchDegrees=pitch,
    )


def test_blink_detected_on_open_close_open_transition():
    settings = Settings()
    frames = [_frame(blink=0.1), _frame(blink=0.1), _frame(blink=0.9), _frame(blink=0.05)]
    assert verify_action("BLINK", frames, settings) is True


def test_blink_not_detected_if_eyes_stay_open():
    settings = Settings()
    frames = [_frame(blink=0.1), _frame(blink=0.15), _frame(blink=0.1)]
    assert verify_action("BLINK", frames, settings) is False


def test_blink_not_detected_if_eyes_stay_closed_the_whole_time():
    # Une photo statique yeux fermés ne doit pas passer : il faut la transition complète.
    settings = Settings()
    frames = [_frame(blink=0.9), _frame(blink=0.9), _frame(blink=0.9)]
    assert verify_action("BLINK", frames, settings) is False


def test_smile_detected_when_any_frame_crosses_threshold():
    settings = Settings()
    frames = [_frame(smile=0.1), _frame(smile=0.8)]
    assert verify_action("SMILE", frames, settings) is True


def test_smile_not_detected_when_below_threshold_throughout():
    settings = Settings()
    frames = [_frame(smile=0.1), _frame(smile=0.2)]
    assert verify_action("SMILE", frames, settings) is False


def test_turn_left_detected_via_positive_yaw_delta_from_baseline():
    # Convention confirmée par un vrai test caméra le 2026-07-31 : delta_yaw > 0 = tête tournée
    # à gauche (inverse de l'hypothèse initiale, voir actions.py).
    settings = Settings()
    frames = [_frame(yaw=-2.0), _frame(yaw=20.0)]
    assert verify_action("TURN_LEFT", frames, settings) is True
    assert verify_action("TURN_RIGHT", frames, settings) is False


def test_turn_right_detected_via_negative_yaw_delta_from_baseline():
    settings = Settings()
    frames = [_frame(yaw=1.0), _frame(yaw=-25.0)]
    assert verify_action("TURN_RIGHT", frames, settings) is True
    assert verify_action("TURN_LEFT", frames, settings) is False


def test_turn_not_detected_below_threshold():
    settings = Settings()
    frames = [_frame(yaw=0.0), _frame(yaw=5.0)]
    assert verify_action("TURN_LEFT", frames, settings) is False
    assert verify_action("TURN_RIGHT", frames, settings) is False


def test_look_up_and_down_use_pitch_delta_from_baseline():
    # Convention confirmée par un vrai test caméra le 2026-07-31 : delta_pitch < 0 = tête levée,
    # > 0 = tête baissée (inverse de l'hypothèse initiale, voir actions.py).
    settings = Settings()
    up_frames = [_frame(pitch=0.0), _frame(pitch=-20.0)]
    down_frames = [_frame(pitch=0.0), _frame(pitch=20.0)]

    assert verify_action("LOOK_UP", up_frames, settings) is True
    assert verify_action("LOOK_DOWN", up_frames, settings) is False
    assert verify_action("LOOK_DOWN", down_frames, settings) is True
    assert verify_action("LOOK_UP", down_frames, settings) is False


def test_action_fails_with_insufficient_detected_frames():
    settings = Settings()
    frames = [_frame(face_detected=False), _frame(blink=0.9)]
    assert verify_action("BLINK", frames, settings) is False


def test_unknown_action_returns_false():
    settings = Settings()
    frames = [_frame(), _frame()]
    assert verify_action("DO_A_BACKFLIP", frames, settings) is False
