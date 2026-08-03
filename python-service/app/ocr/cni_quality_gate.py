import cv2

from app.config.settings import Settings, get_settings
from app.utils.image_io import decode_image
from app.vision.quality import check_resolution, compute_blur_score, compute_brightness, detect_glare


def verify_cni_quality(
    front_bytes: bytes, back_bytes: bytes | None = None, settings: Settings | None = None
) -> tuple[bool, list[str]]:
    """Vérifie la qualité physique de l'image (flou, obscurité, reflets, résolution)

    Si l'image présente des défauts de prise de vue critiques (flou excessif, reflet,
    obscurité, résolution trop basse), cette fonction retourne (False, issues)
    afin de bloquer l'OCR et demander une nouvelle photo.
    """
    settings = settings or get_settings()
    issues: list[str] = []

    _check_image_quality(front_bytes, "Recto", settings, issues)
    if back_bytes and len(back_bytes) > 0:
        _check_image_quality(back_bytes, "Verso", settings, issues)

    is_quality_valid = len(issues) == 0
    return is_quality_valid, issues


def _check_image_quality(raw_bytes: bytes, side_label: str, settings: Settings, issues: list[str]) -> None:
    try:
        image = decode_image(raw_bytes)
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    except Exception:
        issues.append(f"[{side_label}] Image invalide ou illisible")
        return

    blur_score = compute_blur_score(gray)
    brightness = compute_brightness(gray)
    resolution_ok, _, _ = check_resolution(image, settings)

    if blur_score < settings.min_blur_variance:
        issues.append(f"[{side_label}] Image floue : reprenez la photo en stabilisant l'appareil")
    if brightness < settings.min_brightness:
        issues.append(f"[{side_label}] Luminosité insuffisante : rapprochez-vous d'une source de lumière")
    if not resolution_ok:
        issues.append(
            f"[{side_label}] Résolution trop faible (minimum {settings.min_resolution_width}x{settings.min_resolution_height})"
        )
