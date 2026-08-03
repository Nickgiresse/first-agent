from dataclasses import dataclass

import cv2
import numpy as np

from app.vision.detector import find_document_contour

_MAX_HEIGHT_FOR_OCR = 2400  # borne haute pour éviter un traitement inutilement long sur les grandes photos
_MIN_CONTOUR_AREA_RATIO_FOR_WARP = 0.15  # sous ce seuil, le contour détecté est jugé peu fiable


@dataclass(frozen=True)
class PreprocessingProfile:
    """Chaque étape du pipeline est activable/désactivable indépendamment, pour pouvoir comparer
    les variantes (voir benchmark_ocr.py) sans dupliquer le code de prétraitement."""

    correct_perspective: bool = True
    channel_mode: str = "gray"  # "gray" | "red" | "green" | "blue" | "min_channel"
    apply_clahe: bool = False
    pale_document: bool = False  # active la binarisation adaptative (documents scannés/pâles)
    upscale_factor: float = 1.5
    bypass: bool = False  # ignore tout le pipeline, retourne l'image (convertie en gris) telle quelle


# Comportement historique de ce module, déjà calibré sur de vrais documents camerounais (voir
# _flatten_background) : ni CLAHE ni binarisation dure, correction douce uniquement.
DEFAULT_PROFILE = PreprocessingProfile()

# Reçus de paiement / titres provisoires : documents scannés/photocopiés, souvent pâles, avec un
# filigrane "TRAVAIL" en fond — un problème différent des CNI (encre trop claire, pas fond
# guilloché coloré), pour lequel la binarisation adaptative aide généralement plutôt que nuit.
PALE_DOCUMENT_PROFILE = PreprocessingProfile(pale_document=True, upscale_factor=2.0)

# Pour comparer "avec" vs "sans" prétraitement (benchmark_ocr.py notamment).
BYPASS_PROFILE = PreprocessingProfile(bypass=True)


def preprocess_for_ocr(image_bgr: np.ndarray, profile: PreprocessingProfile | None = None) -> np.ndarray:
    """Pipeline de prétraitement pour Tesseract, paramétrable par profil (voir PreprocessingProfile).

    Le profil par défaut reproduit exactement le comportement historique de ce module : ni
    binarisation dure (adaptiveThreshold) ni contraste local (CLAHE), les deux ayant dégradé la
    lecture sur de vrais documents camerounais testés (CLAHE amplifie les tampons/filigranes ;
    la binarisation fragmente les traits fins, ex. un "K" mal binarisé lu "IX"). Ces étapes restent
    disponibles mais désactivées par défaut — seul PALE_DOCUMENT_PROFILE active la binarisation,
    pour une famille de documents différente (reçus/provisoires scannés) où ce compromis est
    généralement favorable.
    """
    profile = profile or DEFAULT_PROFILE

    if profile.bypass:
        return image_bgr if image_bgr.ndim == 2 else cv2.cvtColor(image_bgr, cv2.COLOR_BGR2GRAY)

    working = image_bgr
    if profile.correct_perspective and working.ndim == 3:
        working = _correct_perspective(working)

    channel = _to_single_channel(working, profile.channel_mode)
    channel = cv2.bilateralFilter(channel, d=7, sigmaColor=50, sigmaSpace=50)
    channel = _flatten_background(channel)

    if profile.apply_clahe:
        channel = _apply_clahe(channel)

    channel = _upscale(channel, profile.upscale_factor)

    # La binarisation doit rester la toute dernière étape : appliquée avant l'agrandissement, le
    # rééchantillonnage (interpolation cubique) réintroduit des niveaux de gris intermédiaires sur
    # les bords, ce qui n'est plus vraiment "binaire" en sortie.
    if profile.pale_document:
        channel = _adaptive_threshold(channel)

    return channel


def _correct_perspective(image_bgr: np.ndarray) -> np.ndarray:
    """Détecte le contour du document (réutilise la détection du Module 1, voir app/vision/detector.py)
    et le redresse en vue de face. Si aucun contour plausible n'est trouvé, retourne l'image telle
    quelle plutôt que de risquer une déformation sur un faux contour."""
    contour = find_document_contour(image_bgr)
    if contour is None or contour.area_ratio < _MIN_CONTOUR_AREA_RATIO_FOR_WARP or len(contour.points) != 4:
        return image_bgr

    ordered = _order_corners(contour.points)
    top_left, top_right, bottom_right, bottom_left = ordered

    target_width = int(max(np.linalg.norm(top_right - top_left), np.linalg.norm(bottom_right - bottom_left)))
    target_height = int(max(np.linalg.norm(bottom_left - top_left), np.linalg.norm(bottom_right - top_right)))
    if target_width < 10 or target_height < 10:
        return image_bgr

    destination = np.array(
        [[0, 0], [target_width - 1, 0], [target_width - 1, target_height - 1], [0, target_height - 1]],
        dtype=np.float32,
    )
    matrix = cv2.getPerspectiveTransform(ordered.astype(np.float32), destination)
    return cv2.warpPerspective(image_bgr, matrix, (target_width, target_height))


def _order_corners(points: np.ndarray) -> np.ndarray:
    """Trie 4 points en (haut-gauche, haut-droite, bas-droite, bas-gauche) : find_document_contour
    ne garantit aucun ordre particulier, et cv2.getPerspectiveTransform exige une correspondance
    cohérente entre points source et destination."""
    points = points.astype(np.float32)
    sums = points.sum(axis=1)
    diffs = np.diff(points, axis=1).flatten()
    top_left = points[np.argmin(sums)]
    bottom_right = points[np.argmax(sums)]
    top_right = points[np.argmin(diffs)]
    bottom_left = points[np.argmax(diffs)]
    return np.array([top_left, top_right, bottom_right, bottom_left])


def _to_single_channel(image_bgr: np.ndarray, mode: str) -> np.ndarray:
    if image_bgr.ndim == 2:
        return image_bgr
    if mode == "gray":
        return cv2.cvtColor(image_bgr, cv2.COLOR_BGR2GRAY)
    if mode == "red":
        return image_bgr[:, :, 2]
    if mode == "green":
        return image_bgr[:, :, 1]
    if mode == "blue":
        return image_bgr[:, :, 0]
    if mode == "min_channel":
        # Le texte noir est sombre sur les 3 canaux à la fois ; un fond coloré (guilloché) a
        # toujours au moins un canal clair. Prendre le minimum par pixel fait donc ressortir le
        # texte tout en assombrissant les motifs de fond colorés.
        return np.min(image_bgr, axis=2).astype(np.uint8)
    raise ValueError(f"Mode de canal inconnu : {mode!r}")


def _apply_clahe(gray: np.ndarray) -> np.ndarray:
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    return clahe.apply(gray)


def _adaptive_threshold(gray: np.ndarray) -> np.ndarray:
    return cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 31, 15)


def _flatten_background(gray: np.ndarray) -> np.ndarray:
    """Atténue les fonds guillochés/dégradés de couleur en divisant l'image par une estimation
    floue (basse fréquence) de son propre arrière-plan local, plutôt que par un contraste global
    (CLAHE) ou un seuillage dur. Le texte, dont les traits sont fins et varient rapidement, survit
    au flou et ressort donc plus net après division ; les variations lentes (dégradés, motifs de
    fond) sont elles aplaties car quasi égales à leur propre estimation floue."""
    background = cv2.GaussianBlur(gray, (0, 0), sigmaX=25)
    return cv2.divide(gray, background, scale=255)


def _upscale(gray: np.ndarray, factor: float) -> np.ndarray:
    height = gray.shape[0]
    target_height = min(height * factor, _MAX_HEIGHT_FOR_OCR)
    if target_height <= height:
        return gray
    scale = target_height / height
    return cv2.resize(gray, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
