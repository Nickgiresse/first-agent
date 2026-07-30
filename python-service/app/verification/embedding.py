from functools import lru_cache
from pathlib import Path

import cv2
import numpy as np
import onnxruntime as ort

_MODEL_PATH = Path(__file__).resolve().parent.parent.parent / "weights" / "w600k_r50.onnx"


@lru_cache
def _get_session() -> ort.InferenceSession:
    """Session ONNX Runtime chargée une seule fois (singleton). Modèle ArcFace (w600k_r50,
    InsightFace buffalo_l) : entrée (1,3,112,112) RGB normalisée, sortie empreinte 512-d —
    structure vérifiée directement sur le fichier via session.get_inputs()/get_outputs()."""
    if not _MODEL_PATH.exists():
        raise FileNotFoundError(
            f"Modèle ArcFace introuvable : {_MODEL_PATH}. Téléchargez-le depuis "
            "https://huggingface.co/public-data/insightface/resolve/main/models/buffalo_l/"
            "w600k_r50.onnx vers le dossier weights/."
        )
    return ort.InferenceSession(str(_MODEL_PATH), providers=["CPUExecutionProvider"])


def extract_embedding(aligned_face_bgr: np.ndarray) -> np.ndarray:
    """Calcule l'empreinte faciale (512 dimensions) d'un visage déjà aligné en 112x112 (voir
    alignment.align_face)."""
    session = _get_session()
    blob = _preprocess(aligned_face_bgr)
    input_name = session.get_inputs()[0].name
    output = session.run(None, {input_name: blob})[0]
    return output[0]


def _preprocess(aligned_face_bgr: np.ndarray) -> np.ndarray:
    rgb = cv2.cvtColor(aligned_face_bgr, cv2.COLOR_BGR2RGB).astype(np.float32)
    normalized = (rgb - 127.5) / 128.0
    chw = np.transpose(normalized, (2, 0, 1))
    return np.expand_dims(chw, axis=0)
