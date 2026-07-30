from dataclasses import dataclass, field
from functools import lru_cache
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np
from mediapipe.tasks.python.core.base_options import BaseOptions
from mediapipe.tasks.python.vision import FaceDetector, FaceDetectorOptions

# mediapipe >= 0.10.x a retiré l'ancienne API `mp.solutions.*` : la détection de visage passe
# désormais par la "Tasks API", qui charge un modèle .tflite explicite (non bundlé dans le pip package).
_MODEL_PATH = Path(__file__).resolve().parent.parent.parent / "weights" / "blaze_face_short_range.tflite"

# Ordre des 6 repères renvoyés par ce modèle (BlazeFace short-range), documenté par Google :
# œil gauche, œil droit, nez, bouche, tragus gauche, tragus droit. Coordonnées normalisées (0-1)
# relatives à l'image entière (à confirmer empiriquement au premier test sur un vrai selfie).
LEFT_EYE, RIGHT_EYE, NOSE_TIP, MOUTH, LEFT_EAR_TRAGION, RIGHT_EAR_TRAGION = range(6)


@dataclass
class FaceKeypoint:
    x: float
    y: float


@dataclass
class FaceBox:
    x: int
    y: int
    width: int
    height: int
    confidence: float
    keypoints: list[FaceKeypoint] = field(default_factory=list)


@lru_cache
def _get_face_detector() -> FaceDetector:
    """Modèle chargé une seule fois (singleton) : l'initialisation MediaPipe n'est pas gratuite."""
    if not _MODEL_PATH.exists():
        raise FileNotFoundError(
            f"Modèle de détection de visage introuvable : {_MODEL_PATH}. "
            "Téléchargez-le depuis "
            "https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/"
            "float16/latest/blaze_face_short_range.tflite vers le dossier weights/."
        )
    options = FaceDetectorOptions(
        base_options=BaseOptions(model_asset_path=str(_MODEL_PATH)),
        min_detection_confidence=0.5,
    )
    return FaceDetector.create_from_options(options)


def detect_faces(image_bgr: np.ndarray) -> list[FaceBox]:
    """Détecte les visages présents dans l'image. Utilisé pour le recto/verso (Module 1) et la
    qualité du selfie (Module 3)."""
    detector = _get_face_detector()
    rgb_image = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2RGB)
    mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_image)
    result = detector.detect(mp_image)

    faces: list[FaceBox] = []
    for detection in result.detections:
        box = detection.bounding_box
        confidence = detection.categories[0].score if detection.categories else 0.0
        keypoints = [FaceKeypoint(x=kp.x, y=kp.y) for kp in (detection.keypoints or [])]
        faces.append(
            FaceBox(
                x=max(box.origin_x, 0),
                y=max(box.origin_y, 0),
                width=box.width,
                height=box.height,
                confidence=float(confidence or 0.0),
                keypoints=keypoints,
            )
        )
    return faces
