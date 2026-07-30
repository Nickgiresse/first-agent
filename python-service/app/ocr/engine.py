import os
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import pytesseract
from PIL import Image

from app.config.settings import Settings, get_settings

_TESSDATA_DIR = Path(__file__).resolve().parent.parent.parent / "weights" / "tessdata"


@dataclass
class OcrWord:
    text: str
    confidence: float
    left: int
    top: int
    width: int
    height: int


def _configure(settings: Settings) -> None:
    pytesseract.pytesseract.tesseract_cmd = settings.tesseract_cmd
    # Pack de langue FR/EN embarqué dans le projet (weights/tessdata), indépendant de ce que
    # l'installation système de Tesseract fournit par défaut (souvent l'anglais seul).
    os.environ["TESSDATA_PREFIX"] = str(_TESSDATA_DIR)


def extract_words(image: np.ndarray, settings: Settings | None = None) -> list[OcrWord]:
    """Fait tourner Tesseract sur une image déjà prétraitée ; retourne chaque mot détecté avec
    sa position et sa confiance individuelle (0-100)."""
    settings = settings or get_settings()
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
    """Texte brut complet (lignes préservées), utilisé par cni_parser.py pour le repérage
    des libellés de champs."""
    settings = settings or get_settings()
    _configure(settings)
    pil_image = Image.fromarray(image)
    return pytesseract.image_to_string(pil_image, lang=settings.ocr_languages)
