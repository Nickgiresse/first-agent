from dataclasses import dataclass

import cv2
import numpy as np

from app.config.settings import Settings


@dataclass
class DocumentContour:
    points: np.ndarray          # 4 points (x, y) du quadrilatère détecté, dans l'image d'origine
    area_ratio: float           # surface du contour / surface de l'image
    aspect_ratio: float         # côté long / côté court du rectangle englobant
    bounding_box: tuple[int, int, int, int]  # x, y, w, h


def find_document_contour(image: np.ndarray) -> DocumentContour | None:
    """Cherche le plus grand quadrilatère de l'image, candidat naturel pour une carte posée sur un fond."""
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    blurred = cv2.GaussianBlur(gray, (5, 5), 0)
    edges = cv2.Canny(blurred, 50, 150)
    edges = cv2.dilate(edges, np.ones((5, 5), np.uint8), iterations=1)

    contours, _ = cv2.findContours(edges, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return None

    image_area = image.shape[0] * image.shape[1]
    best_candidate: DocumentContour | None = None
    best_area = 0.0

    for contour in sorted(contours, key=cv2.contourArea, reverse=True)[:10]:
        area = cv2.contourArea(contour)
        if area <= best_area or area < 0.05 * image_area:
            continue

        perimeter = cv2.arcLength(contour, True)
        approx = cv2.approxPolyDP(contour, 0.02 * perimeter, True)

        # On accepte les contours à 4 sommets (carte bien cadrée) ainsi que le rectangle
        # englobant des contours plus complexes (carte partiellement occlue, angles arrondis).
        x, y, w, h = cv2.boundingRect(contour)
        rect_points = np.array([[x, y], [x + w, y], [x + w, y + h], [x, y + h]], dtype=np.int32)
        points = approx.reshape(-1, 2) if len(approx) == 4 else rect_points

        long_side, short_side = max(w, h), max(min(w, h), 1)
        best_candidate = DocumentContour(
            points=points,
            area_ratio=area / image_area,
            aspect_ratio=long_side / short_side,
            bounding_box=(x, y, w, h),
        )
        best_area = area

    return best_candidate


def is_fully_visible(contour: DocumentContour, image_shape: tuple[int, int], settings: Settings) -> bool:
    """Un document dont un coin touche le bord de l'image est probablement rogné/mal cadré."""
    height, width = image_shape[:2]
    margin_x = width * settings.border_margin_ratio
    margin_y = height * settings.border_margin_ratio

    for x, y in contour.points:
        if x <= margin_x or x >= width - margin_x or y <= margin_y or y >= height - margin_y:
            return False
    return True


def matches_id_card_shape(contour: DocumentContour, settings: Settings) -> bool:
    """Compare le ratio largeur/hauteur détecté au format standard CR80 (1,586) des CNI."""
    lower = settings.cni_aspect_ratio - settings.cni_aspect_ratio_tolerance
    upper = settings.cni_aspect_ratio + settings.cni_aspect_ratio_tolerance
    return lower <= contour.aspect_ratio <= upper
