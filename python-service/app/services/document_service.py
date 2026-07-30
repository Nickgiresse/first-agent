import cv2

from app.config.settings import Settings, get_settings
from app.models.document_models import (
    DocumentAnalyzeResponse,
    DocumentSide,
    QualityDetails,
    Resolution,
)
from app.utils.image_io import decode_image
from app.vision.detector import find_document_contour, is_fully_visible, matches_id_card_shape
from app.vision.quality import check_resolution, compute_blur_score, compute_brightness, detect_glare
from app.vision.side_classifier import classify_side


def analyze_document(raw_bytes: bytes, settings: Settings | None = None) -> DocumentAnalyzeResponse:
    settings = settings or get_settings()
    image = decode_image(raw_bytes)
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)

    issues: list[str] = []

    blur_score = compute_blur_score(gray)
    brightness = compute_brightness(gray)
    glare_detected, glare_ratio = detect_glare(gray, settings)
    resolution_ok, width, height = check_resolution(image, settings)

    if blur_score < settings.min_blur_variance:
        issues.append("Image floue : reprenez la photo en stabilisant l'appareil")
    if brightness < settings.min_brightness:
        issues.append("Luminosité insuffisante : rapprochez-vous d'une source de lumière")
    elif brightness > settings.max_brightness:
        issues.append("Luminosité excessive : évitez la lumière directe")
    if glare_detected:
        issues.append("Reflet détecté : inclinez légèrement le document")
    if not resolution_ok:
        issues.append(
            f"Résolution trop faible (minimum {settings.min_resolution_width}x{settings.min_resolution_height})"
        )

    contour = find_document_contour(image)
    document_detected = contour is not None and contour.area_ratio >= settings.min_document_area_ratio
    fully_visible = document_detected and is_fully_visible(contour, image.shape, settings)
    shape_matches = document_detected and matches_id_card_shape(contour, settings)

    if not document_detected:
        issues.append("Aucun document détecté : placez la CNI entièrement dans le cadre")
    elif not fully_visible:
        issues.append("Le document n'est pas entièrement visible dans le cadre")
    elif not shape_matches:
        issues.append("La forme détectée ne correspond pas au format standard d'une CNI")

    side = classify_side(image) if document_detected else DocumentSide.UNKNOWN
    document_type = "CNI_CAMEROON" if document_detected and shape_matches else None

    quality_score = _compute_quality_score(
        blur_score=blur_score,
        brightness=brightness,
        glare_ratio=glare_ratio,
        resolution_ok=resolution_ok,
        document_detected=document_detected,
        fully_visible=fully_visible,
        shape_matches=shape_matches,
        settings=settings,
    )

    return DocumentAnalyzeResponse(
        documentDetected=document_detected,
        documentType=document_type,
        typeConfirmed=False,  # ne sera fiable qu'une fois le Module 2 (OCR) branché en confirmation
        side=side,
        qualityScore=quality_score,
        qualityDetails=QualityDetails(
            blurScore=round(blur_score, 2),
            brightness=round(brightness, 2),
            glareDetected=glare_detected,
            glareRatio=round(glare_ratio, 4),
            resolution=Resolution(width=width, height=height),
            fullyVisible=fully_visible,
            documentAreaRatio=round(contour.area_ratio, 4) if contour else 0.0,
        ),
        issues=issues,
    )


def _compute_quality_score(
    *,
    blur_score: float,
    brightness: float,
    glare_ratio: float,
    resolution_ok: bool,
    document_detected: bool,
    fully_visible: bool,
    shape_matches: bool,
    settings: Settings,
) -> int:
    blur_component = min(blur_score / (settings.min_blur_variance * 2), 1.0)

    if settings.min_brightness <= brightness <= settings.max_brightness:
        brightness_component = 1.0
    elif brightness < settings.min_brightness:
        brightness_component = max(brightness / settings.min_brightness, 0.0)
    else:
        brightness_component = max((255 - brightness) / (255 - settings.max_brightness), 0.0)

    glare_component = max(1.0 - (glare_ratio / (settings.glare_area_ratio_threshold * 2)), 0.0)
    resolution_component = 1.0 if resolution_ok else 0.0
    framing_component = sum([document_detected, fully_visible, shape_matches]) / 3

    score = (
        0.30 * blur_component
        + 0.15 * brightness_component
        + 0.15 * glare_component
        + 0.10 * resolution_component
        + 0.30 * framing_component
    )
    return round(max(0.0, min(score, 1.0)) * 100)
