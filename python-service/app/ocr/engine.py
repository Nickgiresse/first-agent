"""
app/ocr/engine.py — Moteur OCR RapidOCR (PP-OCRv4 / latin ONNX).

Remplace Tesseract (fusion « best of both » : moteur porté du WhatsApp banking,
éprouvé sur CNI camerounaises réelles). RapidOCR est un moteur de deep learning
en ONNX Runtime, nettement plus précis que Tesseract sur photo de pièce
d'identité. Le contrat public est INCHANGÉ — extract_words() / extract_text()
restent consommés tels quels par cni_parser et ocr_service, sans rien modifier
en aval.

Optimisations portées du service éprouvé :
  - chargement paresseux thread-safe du moteur (le premier chargement prend
    quelques secondes : on ne ralentit pas le démarrage) ;
  - verrou d'inférence unique : deux inférences ONNX concurrentes se disputent
    les cœurs (sur-souscription intra-op) et deviennent 7 à 15x plus lentes ;
  - opt-out EcoQoS Windows : les processus de fond sont relégués sur les cœurs
    efficaces, bridant l'ONNX x4-8 — on s'en retire au chargement ;
  - warmup() optionnel pour ne pas payer l'optimisation de graphe ONNX sur la
    première vraie requête (respect du SLA KYC).

Aucune image n'est conservée : tout est traité en mémoire (numpy).

Modèle latin PP-OCRv3 optionnel (meilleure précision sur texte français) :
déposer latin_PP-OCRv3_rec_infer.onnx + latin_dict.txt dans weights/ocr_models/.
Repli automatique sur les modèles par défaut du wheel sinon.
"""
from __future__ import annotations

import logging
import os
import threading
from dataclasses import dataclass
from pathlib import Path

import numpy as np

from app.config.settings import Settings  # signature préservée (settings ignoré : RapidOCR gère FR/EN)

logger = logging.getLogger("ocr.engine")

_MODEL_DIR = Path(__file__).resolve().parents[2] / "weights" / "ocr_models"

_ocr = None
_ocr_lock = threading.Lock()
_ocr_failed = False

# Une seule inférence RapidOCR à la fois par processus (cf. docstring).
_infer_lock = threading.Lock()


@dataclass
class OcrWord:
    text: str
    confidence: float          # 0-100 (comme Tesseract), ici score RapidOCR x100
    left: int
    top: int
    width: int
    height: int


def _opt_out_ecoqos() -> None:
    """Retire le processus du throttling EcoQoS (Windows) pour l'inférence ONNX."""
    if os.name != "nt":
        return
    try:
        import ctypes
        from ctypes import wintypes

        class _PPS(ctypes.Structure):
            _fields_ = [("Version", wintypes.ULONG),
                        ("ControlMask", wintypes.ULONG),
                        ("StateMask", wintypes.ULONG)]

        # Version=1, ControlMask=EXECUTION_SPEED(0x1), StateMask=0 => opt-out.
        info = _PPS(1, 0x1, 0)
        kernel32 = ctypes.windll.kernel32
        # ProcessPowerThrottling = 4
        kernel32.SetProcessInformation(kernel32.GetCurrentProcess(), 4,
                                       ctypes.byref(info), ctypes.sizeof(info))
    except Exception as exc:  # best-effort, jamais bloquant
        logger.debug("EcoQoS opt-out ignoré : %s", exc)


def _get_ocr():
    """Charge RapidOCR au premier appel (thread-safe). None si indisponible."""
    global _ocr, _ocr_failed
    if _ocr is not None or _ocr_failed:
        return _ocr
    with _ocr_lock:
        if _ocr is not None or _ocr_failed:
            return _ocr
        _opt_out_ecoqos()
        try:
            from rapidocr_onnxruntime import RapidOCR
            model = _MODEL_DIR / "latin_PP-OCRv3_rec_infer.onnx"
            keys = _MODEL_DIR / "latin_dict.txt"
            if model.exists() and keys.exists():
                logger.info("[OCR] RapidOCR + modèle latin (%s)", model.name)
                _ocr = RapidOCR(rec_model_path=str(model), rec_keys_path=str(keys),
                                det_limit_side_len=1536)
            else:
                logger.info("[OCR] RapidOCR modèle par défaut (latin absent de %s)", _MODEL_DIR)
                _ocr = RapidOCR()
        except Exception as exc:
            logger.error("[OCR] RapidOCR indisponible : %s", exc)
            _ocr_failed = True
    return _ocr


def warmup() -> None:
    """Pré-charge le moteur + une inférence factice (à appeler au démarrage)."""
    ocr = _get_ocr()
    if ocr is None:
        return
    try:
        dummy = np.full((64, 256, 3), 255, dtype=np.uint8)
        with _infer_lock:
            ocr(dummy)
        logger.info("[OCR] warmup RapidOCR terminé")
    except Exception as exc:
        logger.debug("[OCR] warmup ignoré : %s", exc)


def _to_bgr(image: np.ndarray) -> np.ndarray:
    """RapidOCR attend une image 3 canaux uint8 : convertit gris/float si besoin."""
    arr = image
    if arr.dtype != np.uint8:
        arr = np.clip(arr, 0, 255).astype(np.uint8)
    if arr.ndim == 2:
        import cv2
        arr = cv2.cvtColor(arr, cv2.COLOR_GRAY2BGR)
    elif arr.ndim == 3 and arr.shape[2] == 4:
        import cv2
        arr = cv2.cvtColor(arr, cv2.COLOR_BGRA2BGR)
    return arr


def _run(image: np.ndarray) -> list:
    """Inférence RapidOCR sérialisée. Retourne une liste de [box, texte, score]."""
    ocr = _get_ocr()
    if ocr is None:
        return []
    with _infer_lock:
        result, _ = ocr(_to_bgr(image))
    return result or []


def _box_bounds(box):
    """Boîte RapidOCR (4 points) → (left, top, width, height) entiers."""
    xs = [p[0] for p in box]
    ys = [p[1] for p in box]
    left, top = int(min(xs)), int(min(ys))
    return left, top, int(max(xs)) - left, int(max(ys)) - top


def _tesseract_fallback_words(image: np.ndarray, settings: Settings | None = None) -> list[OcrWord]:
    import pytesseract
    from PIL import Image
    cfg = settings or Settings()
    pytesseract.pytesseract.tesseract_cmd = cfg.tesseract_cmd
    pil_img = Image.fromarray(image) if isinstance(image, np.ndarray) else image
    data = pytesseract.image_to_data(pil_img, lang=cfg.ocr_languages, output_type=pytesseract.Output.DICT)
    words = []
    for i in range(len(data["text"])):
        text = str(data["text"][i]).strip()
        conf = float(data["conf"][i])
        if text and conf > 0:
            words.append(OcrWord(
                text=text,
                confidence=conf,
                left=int(data["left"][i]),
                top=int(data["top"][i]),
                width=int(data["width"][i]),
                height=int(data["height"][i])
            ))
    return words


def _tesseract_fallback_text(image: np.ndarray, settings: Settings | None = None) -> str:
    import pytesseract
    from PIL import Image
    cfg = settings or Settings()
    pytesseract.pytesseract.tesseract_cmd = cfg.tesseract_cmd
    pil_img = Image.fromarray(image) if isinstance(image, np.ndarray) else image
    return pytesseract.image_to_string(pil_img, lang=cfg.ocr_languages, config="--psm 6")


def extract_words(image: np.ndarray, settings: Settings | None = None) -> list[OcrWord]:
    """Fait tourner RapidOCR (ou Tesseract en fallback) ; retourne chaque texte détecté
    avec sa position et sa confiance individuelle (0-100)."""
    if _get_ocr() is None:
        return _tesseract_fallback_words(image, settings)
    words: list[OcrWord] = []
    for row in _run(image):
        try:
            box, text, score = row[0], str(row[1]).strip(), float(row[2])
        except (IndexError, TypeError, ValueError):
            continue
        if not text:
            continue
        left, top, width, height = _box_bounds(box)
        words.append(OcrWord(text=text, confidence=round(score * 100, 2),
                             left=left, top=top, width=width, height=height))
    return words


def extract_text(image: np.ndarray, settings: Settings | None = None) -> str:
    """Texte à lignes préservées (utilisé par cni_parser.py pour le repérage des
    libellés). RapidOCR (ou Tesseract en fallback).
    """
    if _get_ocr() is None:
        return _tesseract_fallback_text(image, settings)
    items = []
    for row in _run(image):
        try:
            box, text = row[0], str(row[1]).strip()
        except (IndexError, TypeError):
            continue
        if not text:
            continue
        left, top, _, height = _box_bounds(box)
        items.append((top, height, left, text))
    if not items:
        return ""

    items.sort(key=lambda t: (t[0], t[2]))          # haut→bas puis gauche→droite
    lines: list[list] = []
    for top, height, left, text in items:
        placed = False
        for line in lines:
            ref_top, ref_h = line[0][0], line[0][1]
            # même ligne si les centres verticaux sont proches (< 60 % de la hauteur)
            if abs((top + height / 2) - (ref_top + ref_h / 2)) <= max(height, ref_h) * 0.6:
                line.append((top, height, left, text))
                placed = True
                break
        if not placed:
            lines.append([(top, height, left, text)])

    out_lines = []
    for line in lines:
        line.sort(key=lambda t: t[2])               # gauche → droite
        out_lines.append(" ".join(t[3] for t in line))
    return "\n".join(out_lines)
