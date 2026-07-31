"""Extraction de texte, avec deux moteurs interchangeables.

POURQUOI DEUX MOTEURS
---------------------
Tesseract a été retenu par défaut faute de wheel PaddleOCR pour Python 3.14.
Mesuré sur trois CNI camerounaises réelles, il lit environ un tiers du texte
que lit RapidOCR : 228 caractères contre 607 sur la même image. Conséquence
directe, l'analyseur en aval ne trouve ni nom, ni prénom, ni lieu de
naissance, alors qu'il les extrait correctement dès qu'il est alimenté par
RapidOCR.

RapidOCR devient donc le moteur par défaut. Tesseract reste sélectionnable
par `OCR_ENGINE=tesseract`, car il n'exige aucun modèle ONNX supplémentaire
et rend service là où l'empreinte disque compte.
"""
import os
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image

from app.config.settings import Settings, get_settings

_TESSDATA_DIR = Path(__file__).resolve().parent.parent.parent / "weights" / "tessdata"

# Instance RapidOCR : le chargement des modèles ONNX coûte plusieurs secondes,
# il ne doit pas être refait à chaque page.
_rapidocr = None


def _get_rapidocr():
    global _rapidocr
    if _rapidocr is None:
        from rapidocr_onnxruntime import RapidOCR
        _rapidocr = RapidOCR()
    return _rapidocr


def _rapidocr_lines(image: np.ndarray) -> list[tuple[str, float, list]]:
    """Lignes détectées par RapidOCR : (texte, confiance 0-1, boîte englobante)."""
    resultat, _ = _get_rapidocr()(image)
    return [(t, float(c), box) for box, t, c in (resultat or [])]


@dataclass
class OcrWord:
    text: str
    confidence: float
    left: int
    top: int
    width: int
    height: int


def _configure(settings: Settings) -> None:
    import pytesseract
    pytesseract.pytesseract.tesseract_cmd = settings.tesseract_cmd
    # Pack de langue FR/EN embarqué dans le projet (weights/tessdata), indépendant de ce que
    # l'installation système de Tesseract fournit par défaut (souvent l'anglais seul).
    os.environ["TESSDATA_PREFIX"] = str(_TESSDATA_DIR)


def extract_words(image: np.ndarray, settings: Settings | None = None) -> list[OcrWord]:
    """Mots détectés avec position et confiance individuelle (0-100)."""
    settings = settings or get_settings()

    if getattr(settings, "ocr_engine", "rapidocr") == "rapidocr":
        mots: list[OcrWord] = []
        for texte, conf, boite in _rapidocr_lines(image):
            propre = (texte or "").strip()
            if not propre:
                continue
            xs = [int(p[0]) for p in boite]
            ys = [int(p[1]) for p in boite]
            # RapidOCR rend une LIGNE, pas un mot. La boîte est celle de la
            # ligne entière : la position par mot serait fausse si on la
            # découpait, on conserve donc la ligne telle quelle.
            mots.append(OcrWord(text=propre, confidence=conf * 100.0,
                                left=min(xs), top=min(ys),
                                width=max(xs) - min(xs), height=max(ys) - min(ys)))
        return mots

    import pytesseract
    _configure(settings)
    pil_image = Image.fromarray(image)
    data = pytesseract.image_to_data(
        pil_image, lang=settings.ocr_languages, output_type=pytesseract.Output.DICT
    )

    words: list[OcrWord] = []
    for i, text in enumerate(data["text"]):
        cleaned = text.strip()
        if not cleaned:
            continue
        confidence = float(data["conf"][i])
        if confidence < 0:  # -1 = pas de texte exploitable sur cette zone
            continue
        words.append(
            OcrWord(
                text=cleaned,
                confidence=confidence,
                left=data["left"][i],
                top=data["top"][i],
                width=data["width"][i],
                height=data["height"][i],
            )
        )
    return words


def extract_text(image: np.ndarray, settings: Settings | None = None) -> str:
    """Texte brut complet, lignes préservées, utilisé par cni_parser.py pour le
    repérage des libellés de champs."""
    settings = settings or get_settings()

    if getattr(settings, "ocr_engine", "rapidocr") == "rapidocr":
        lignes = _rapidocr_lines(image)
        # Remise en ordre de lecture : RapidOCR rend les lignes dans l'ordre de
        # détection, pas de haut en bas. L'analyseur, lui, lit un libellé puis
        # la valeur qui suit — un mauvais ordre lui ferait associer la valeur
        # d'un champ au libellé d'un autre.
        lignes.sort(key=lambda item: (min(int(p[1]) for p in item[2]),
                                      min(int(p[0]) for p in item[2])))
        return "\n".join(t for t, _, _ in lignes)

    import pytesseract
    _configure(settings)
    pil_image = Image.fromarray(image)
    return pytesseract.image_to_string(pil_image, lang=settings.ocr_languages)
