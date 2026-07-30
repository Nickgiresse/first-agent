import cv2

from app.config.settings import Settings, get_settings
from app.face.quality import FaceQualityResult, analyze_face
from app.models.document_models import Resolution
from app.models.face_models import FaceAnalyzeResponse, FaceQualityDetails
from app.utils.image_io import decode_image
from app.vision.quality import check_resolution, compute_blur_score, compute_brightness, detect_glare


def analyze_face_image(raw_bytes: bytes, settings: Settings | None = None) -> FaceAnalyzeResponse:
    settings = settings or get_settings()
    image = decode_image(raw_bytes)
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)

    blur_score = compute_blur_score(gray)
    brightness = compute_brightness(gray)
    glare_detected, glare_ratio = detect_glare(gray, settings)
    resolution_ok, width, height = check_resolution(image, settings)

    face_result = analyze_face(image, settings)

    issues = _collect_issues(face_result, blur_score, brightness, glare_detected, resolution_ok, settings)
    quality_score = _compute_quality_score(face_result, blur_score, brightness, glare_ratio, resolution_ok, settings)

    return FaceAnalyzeResponse(
        faceDetected=face_result.faceDetected,
        faceCount=face_result.faceCount,
        singleFace=face_result.singleFace,
        centered=face_result.centered,
        eyesDetected=face_result.eyesDetected,
        noseDetected=face_result.noseDetected,
        mouthDetected=face_result.mouthDetected,
        qualityScore=quality_score,
        qualityDetails=FaceQualityDetails(
            blurScore=round(blur_score, 2),
            brightness=round(brightness, 2),
            glareDetected=glare_detected,
            glareRatio=round(glare_ratio, 4),
            resolution=Resolution(width=width, height=height),
            faceAreaRatio=face_result.faceAreaRatio,
            offsetX=face_result.offsetX,
            offsetY=face_result.offsetY,
        ),
        issues=issues,
    )


def _collect_issues(
    face_result: FaceQualityResult,
    blur_score: float,
    brightness: float,
    glare_detected: bool,
    resolution_ok: bool,
    settings: Settings,
) -> list[str]:
    issues: list[str] = []

    if not face_result.faceDetected:
        issues.append("Aucun visage détecté")
    elif not face_result.singleFace:
        issues.append(f"{face_result.faceCount} visages détectés : une seule personne doit être visible")
    else:
        if not face_result.centered:
            issues.append("Visage non centré : positionnez votre visage au centre du cadre")
        if not face_result.eyesDetected:
            issues.append("Yeux non détectés distinctement : regardez l'appareil de face")
        if not face_result.noseDetected:
            issues.append("Nez non détecté distinctement")
        if not face_result.mouthDetected:
            issues.append("Bouche non détectée distinctement")

    if blur_score < settings.min_blur_variance:
        issues.append("Image floue : stabilisez l'appareil")
    if brightness < settings.min_brightness:
        issues.append("Luminosité insuffisante : rapprochez-vous d'une source de lumière")
    elif brightness > settings.max_brightness:
        issues.append("Luminosité excessive : évitez la lumière directe")
    if glare_detected:
        issues.append("Reflet détecté")
    if not resolution_ok:
        issues.append(
            f"Résolution trop faible (minimum {settings.min_resolution_width}x{settings.min_resolution_height})"
        )

    return issues


def _compute_quality_score(
    face_result: FaceQualityResult,
    blur_score: float,
    brightness: float,
    glare_ratio: float,
    resolution_ok: bool,
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
    face_component = sum(
        [
            face_result.faceDetected,
            face_result.singleFace,
            face_result.centered,
            face_result.eyesDetected,
            face_result.noseDetected,
            face_result.mouthDetected,
        ]
    ) / 6

    score = (
        0.20 * blur_component
        + 0.10 * brightness_component
        + 0.10 * glare_component
        + 0.10 * resolution_component
        + 0.50 * face_component
    )
    return round(max(0.0, min(score, 1.0)) * 100)
