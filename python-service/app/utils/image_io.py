import io

import cv2
import numpy as np
from PIL import Image, UnidentifiedImageError

MAX_IMAGE_BYTES = 8 * 1024 * 1024  # 8 Mo


class InvalidImageError(ValueError):
    pass


def decode_image(raw_bytes: bytes) -> np.ndarray:
    """Décode des octets image (JPEG/PNG/BMP via OpenCV, AVIF/WEBP/HEIC... via Pillow en repli)
    en image OpenCV (BGR). Lève InvalidImageError si illisible."""
    if not raw_bytes:
        raise InvalidImageError("Fichier image vide")
    if len(raw_bytes) > MAX_IMAGE_BYTES:
        raise InvalidImageError(f"Image trop volumineuse (> {MAX_IMAGE_BYTES // (1024 * 1024)} Mo)")

    buffer = np.frombuffer(raw_bytes, dtype=np.uint8)
    image = cv2.imdecode(buffer, cv2.IMREAD_COLOR)
    if image is not None:
        return image

    # OpenCV ne lit pas certains formats modernes (AVIF, WEBP animé, HEIC...) : repli sur Pillow,
    # qui en supporte davantage, puis conversion RGB -> BGR pour rester cohérent avec le reste du pipeline.
    try:
        pil_image = Image.open(io.BytesIO(raw_bytes)).convert("RGB")
    except UnidentifiedImageError as exc:
        raise InvalidImageError("Impossible de décoder l'image (format non supporté ou fichier corrompu)") from exc

    return cv2.cvtColor(np.array(pil_image), cv2.COLOR_RGB2BGR)
