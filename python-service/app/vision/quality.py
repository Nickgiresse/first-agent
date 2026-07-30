import cv2
import numpy as np

from app.config.settings import Settings


def compute_blur_score(gray: np.ndarray) -> float:
    """Variance du Laplacien : une image nette a une variance élevée (beaucoup de hautes fréquences)."""
    return float(cv2.Laplacian(gray, cv2.CV_64F).var())


def compute_brightness(gray: np.ndarray) -> float:
    return float(gray.mean())


def detect_glare(gray: np.ndarray, settings: Settings) -> tuple[bool, float]:
    """Détecte les reflets via la proportion de pixels saturés (quasi blancs)."""
    saturated_pixels = int(np.count_nonzero(gray >= settings.glare_pixel_threshold))
    ratio = saturated_pixels / gray.size
    return ratio >= settings.glare_area_ratio_threshold, ratio


def check_resolution(image: np.ndarray, settings: Settings) -> tuple[bool, int, int]:
    height, width = image.shape[:2]
    is_sufficient = width >= settings.min_resolution_width and height >= settings.min_resolution_height
    return is_sufficient, width, height
