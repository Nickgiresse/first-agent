import cv2
import numpy as np

_UPSCALE_FACTOR = 1.5
_MAX_HEIGHT_FOR_OCR = 2400  # borne haute pour éviter un traitement inutilement long sur les grandes photos


def preprocess_for_ocr(image_bgr: np.ndarray) -> np.ndarray:
    """Pipeline de prétraitement pour Tesseract : niveaux de gris, réduction de bruit, aplanissement
    du fond, agrandissement.

    Ni binarisation dure (adaptiveThreshold) ni contraste local (CLAHE) : les deux dégradent la
    lecture sur les cas testés (CLAHE amplifie les tampons/filigranes d'un vrai document camerounais
    tamponné ; la binarisation fragmente les traits fins, ex. un "K" mal binarisé lu "IX"). L'ajout du
    2026-07-31 (_flatten_background) est de la même famille prudente que le reste de ce pipeline :
    une correction douce et basse-fréquence plutôt qu'un seuillage dur ou un boost de contraste local,
    pour cibler les fonds guillochés/dégradés de couleur (CNI camerounaise) sans reproduire les
    régressions déjà observées avec CLAHE/adaptiveThreshold. Non vérifiée sur une vraie photo de CNI
    dégradée (pas d'accès à un fichier réel au moment de l'écriture) — seulement sur des images
    synthétiques ; à confirmer/ajuster (sigma de _flatten_background) sur un vrai document si la
    précision reste insuffisante.
    """
    gray = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2GRAY)
    gray = cv2.bilateralFilter(gray, d=7, sigmaColor=50, sigmaSpace=50)
    gray = _flatten_background(gray)
    return _upscale(gray)


def _flatten_background(gray: np.ndarray) -> np.ndarray:
    """Atténue les fonds guillochés/dégradés de couleur en divisant l'image par une estimation
    floue (basse fréquence) de son propre arrière-plan local, plutôt que par un contraste global
    (CLAHE) ou un seuillage dur — voir la note dans preprocess_for_ocr. Le texte, dont les traits
    sont fins et varient rapidement, survit au flou et ressort donc plus net après division ; les
    variations lentes (dégradés, motifs de fond) sont elles aplaties car quasi égales à leur propre
    estimation floue.
    """
    background = cv2.GaussianBlur(gray, (0, 0), sigmaX=25)
    return cv2.divide(gray, background, scale=255)


def _upscale(gray: np.ndarray) -> np.ndarray:
    height = gray.shape[0]
    target_height = min(height * _UPSCALE_FACTOR, _MAX_HEIGHT_FOR_OCR)
    if target_height <= height:
        return gray
    scale = target_height / height
    return cv2.resize(gray, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
