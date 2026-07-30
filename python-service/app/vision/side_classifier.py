import numpy as np

from app.face.detector import detect_faces
from app.models.document_models import DocumentSide


def classify_side(image_bgr: np.ndarray) -> DocumentSide:
    """Le recto d'une CNI porte une photo d'identité (donc un visage détectable) ; le verso non.

    Heuristique volontairement simple : elle sera affinée par le Module 2 (mots-clés OCR,
    ex. présence de la zone MRZ au verso) une fois l'OCR intégré.
    """
    faces = detect_faces(image_bgr)
    if len(faces) >= 1:
        return DocumentSide.FRONT
    return DocumentSide.BACK
