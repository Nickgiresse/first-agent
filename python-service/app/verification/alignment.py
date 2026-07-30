import cv2
import numpy as np

# Gabarit de référence ArcFace : position cible des 5 points dans l'image alignée 112x112.
# Constante standard, identique dans toutes les implémentations ArcFace/InsightFace.
_REFERENCE_112 = np.array(
    [
        [38.2946, 51.6963],
        [73.5318, 51.5014],
        [56.0252, 71.7366],
        [41.5493, 92.3655],
        [70.7299, 92.2041],
    ],
    dtype=np.float32,
)

ALIGNED_SIZE = 112


def align_face(image_bgr: np.ndarray, five_points: np.ndarray) -> np.ndarray:
    """Aligne un visage sur le gabarit standard ArcFace (112x112) à partir de 5 points en pixels
    : [œil-A, œil-B, nez, bouche-A, bouche-B] (segments 0:2, 2:3, 3:5). L'ordre gauche/droite au
    sein de chaque paire n'a pas besoin d'être exact : réassigné par position dans l'image avant
    la transformation, pour ne pas dépendre de la convention gauche/droite du détecteur en amont.
    """
    if five_points.shape != (5, 2):
        raise ValueError(f"5 points (x,y) attendus, reçu un tableau de forme {five_points.shape}")

    ordered = _order_points_like_reference(five_points)
    transform, _ = cv2.estimateAffinePartial2D(ordered, _REFERENCE_112, method=cv2.LMEDS)
    if transform is None:
        raise ValueError("Impossible de calculer l'alignement du visage à partir des repères fournis")
    return cv2.warpAffine(image_bgr, transform, (ALIGNED_SIZE, ALIGNED_SIZE), borderValue=(0, 0, 0))


def _order_points_like_reference(points: np.ndarray) -> np.ndarray:
    eyes = points[:2][np.argsort(points[:2, 0])]
    nose = points[2:3]
    mouth = points[3:][np.argsort(points[3:, 0])]
    return np.vstack([eyes, nose, mouth]).astype(np.float32)
