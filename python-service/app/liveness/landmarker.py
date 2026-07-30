from dataclasses import dataclass, field
from functools import lru_cache
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np
from mediapipe.tasks.python.core.base_options import BaseOptions
from mediapipe.tasks.python.vision import FaceLandmarker, FaceLandmarkerOptions

_MODEL_PATH = Path(__file__).resolve().parent.parent.parent / "weights" / "face_landmarker.task"


@dataclass
class FrameAnalysis:
    faceDetected: bool
    blendshapes: dict[str, float] = field(default_factory=dict)
    yawDegrees: float = 0.0
    pitchDegrees: float = 0.0
    rollDegrees: float = 0.0
    landmarksPx: list[tuple[float, float]] = field(default_factory=list)  # 478 points, pixels image


@lru_cache
def _get_landmarker() -> FaceLandmarker:
    """Modèle chargé une seule fois (singleton) : l'initialisation MediaPipe n'est pas gratuite.

    output_face_blendshapes et output_facial_transformation_matrixes activés : MediaPipe calcule
    déjà des scores d'expression calibrés (ARKit-52, ex. eyeBlinkLeft/Right, mouthSmileLeft/Right)
    et une matrice de pose 3D — pas besoin de recalculer l'EAR ou d'implémenter solvePnP à la main.
    """
    if not _MODEL_PATH.exists():
        raise FileNotFoundError(
            f"Modèle FaceLandmarker introuvable : {_MODEL_PATH}. Téléchargez-le depuis "
            "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/"
            "float16/latest/face_landmarker.task vers le dossier weights/."
        )
    options = FaceLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=str(_MODEL_PATH)),
        num_faces=1,
        output_face_blendshapes=True,
        output_facial_transformation_matrixes=True,
    )
    return FaceLandmarker.create_from_options(options)


def analyze_frame(image_bgr: np.ndarray) -> FrameAnalysis:
    landmarker = _get_landmarker()
    rgb_image = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2RGB)
    mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_image)
    result = landmarker.detect(mp_image)

    if not result.face_landmarks:
        return FrameAnalysis(faceDetected=False)

    blendshapes = {
        category.category_name: category.score for category in (result.face_blendshapes[0] if result.face_blendshapes else [])
    }

    yaw, pitch, roll = 0.0, 0.0, 0.0
    if result.facial_transformation_matrixes:
        yaw, pitch, roll = _rotation_matrix_to_euler_degrees(result.facial_transformation_matrixes[0])

    height, width = image_bgr.shape[:2]
    landmarks_px = [(lm.x * width, lm.y * height) for lm in result.face_landmarks[0]]

    return FrameAnalysis(
        faceDetected=True,
        blendshapes=blendshapes,
        yawDegrees=yaw,
        pitchDegrees=pitch,
        rollDegrees=roll,
        landmarksPx=landmarks_px,
    )


def _rotation_matrix_to_euler_degrees(matrix4x4: np.ndarray) -> tuple[float, float, float]:
    """Décompose la matrice de rotation 3x3 en 3 angles d'Euler (degrés), un par axe.

    ATTENTION — hypothèse non vérifiée empiriquement, point le plus fragile du Module 4 :
    la formule de décomposition ZYX classique associe naturellement rotation-X→"roll",
    rotation-Y→"pitch", rotation-Z→"yaw" (convention aéronautique). En supposant que le modèle
    canonique de visage de MediaPipe suit une convention X-droite/Y-haut/Z-vers-la-caméra (ce qui
    est l'usage courant documenté pour ce type de modèle, mais je n'ai pas pu le confirmer avec une
    vraie photo de tête tournée), la rotation Y (axe vertical) est le vrai "yaw" physique (tourner
    la tête) et la rotation X (axe des oreilles) le vrai "pitch" (hocher la tête) — d'où le mapping
    ci-dessous, qui réattribue les sorties de la formule en conséquence.

    Seule la propriété "pose neutre → angles proches de 0" est vérifiée empiriquement (portrait
    de test MediaPipe officiel). À confirmer avec de vraies photos où la tête est explicitement
    tournée/inclinée avant de faire confiance à ces valeurs en production ; si le sens ou l'axe
    s'avèrent inversés, corriger uniquement ce mapping de retour, le reste du pipeline (blink,
    smile, sessions) n'en dépend pas.
    """
    r = matrix4x4[:3, :3]
    rotation_around_x = np.degrees(np.arctan2(r[2, 1], r[2, 2]))
    rotation_around_y = np.degrees(np.arctan2(-r[2, 0], np.sqrt(r[2, 1] ** 2 + r[2, 2] ** 2)))
    rotation_around_z = np.degrees(np.arctan2(r[1, 0], r[0, 0]))

    yaw = rotation_around_y  # tourner la tête gauche/droite : rotation autour de l'axe vertical (Y)
    pitch = rotation_around_x  # hocher la tête haut/bas : rotation autour de l'axe des oreilles (X)
    roll = rotation_around_z  # incliner la tête épaule à épaule : rotation autour de l'axe de profondeur (Z)
    return float(yaw), float(pitch), float(roll)
