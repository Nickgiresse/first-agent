import cv2
import numpy as np

_UPSCALE_FACTOR = 1.5
_MAX_HEIGHT_FOR_OCR = 2400  # borne haute pour éviter un traitement inutilement long sur les grandes photos


def preprocess_for_ocr(image_bgr: np.ndarray) -> np.ndarray:
    """Pipeline de prétraitement pour Tesseract : niveaux de gris, réduction de bruit, agrandissement.

    Ni binarisation dure (adaptiveThreshold) ni contraste local (CLAHE) : les deux dégradent la
    lecture sur les cas testés (CLAHE amplifie les tampons/filigranes d'un vrai document camerounais
    tamponné ; la binarisation fragmente les traits fins, ex. un "K" mal binarisé lu "IX"). Un simple
    agrandissement après débruitage s'est montré nettement plus fiable — vérifié en comparant les
    variantes sur une vraie photo de titre d'identité provisoire.
    """
    gray = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2GRAY)
    gray = cv2.bilateralFilter(gray, d=7, sigmaColor=50, sigmaSpace=50)
    return _upscale(gray)


def _upscale(gray: np.ndarray) -> np.ndarray:
    height = gray.shape[0]
    target_height = min(height * _UPSCALE_FACTOR, _MAX_HEIGHT_FOR_OCR)
    if target_height <= height:
        return gray
    scale = target_height / height
    return cv2.resize(gray, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
