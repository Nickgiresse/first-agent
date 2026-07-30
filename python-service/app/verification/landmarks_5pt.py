import numpy as np

from app.liveness.landmarker import FrameAnalysis

# Indices dans le maillage à 478 points de MediaPipe Face Mesh (topologie standard, largement
# documentée/réutilisée dans l'écosystème) : nez, un œil, l'autre œil, un coin de bouche, l'autre
# coin de bouche. L'appellation gauche/droite exacte n'a pas d'importance ici (voir alignment.py).
_NOSE_TIP = 1
_EYE_A = 33
_EYE_B = 263
_MOUTH_A = 61
_MOUTH_B = 291


def extract_five_points(frame: FrameAnalysis) -> np.ndarray:
    """Sous-ensemble à 5 points (format attendu par l'alignement ArcFace) à partir du maillage
    complet renvoyé par FaceLandmarker."""
    if len(frame.landmarksPx) <= max(_NOSE_TIP, _EYE_A, _EYE_B, _MOUTH_A, _MOUTH_B):
        raise ValueError("Pas assez de repères détectés pour extraire les 5 points d'alignement")

    points = [
        frame.landmarksPx[_EYE_A],
        frame.landmarksPx[_EYE_B],
        frame.landmarksPx[_NOSE_TIP],
        frame.landmarksPx[_MOUTH_A],
        frame.landmarksPx[_MOUTH_B],
    ]
    return np.array(points, dtype=np.float32)
