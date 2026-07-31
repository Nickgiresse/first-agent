from pathlib import Path

import cv2
import numpy as np
import pytest

_FIXTURES_DIR = Path(__file__).parent / "fixtures"


@pytest.fixture
def sharp_noise_image() -> np.ndarray:
    rng = np.random.default_rng(42)
    return rng.integers(0, 256, size=(600, 800, 3), dtype=np.uint8)


@pytest.fixture
def blurry_image() -> np.ndarray:
    return np.full((600, 800, 3), 128, dtype=np.uint8)


@pytest.fixture
def dark_image() -> np.ndarray:
    return np.full((600, 800, 3), 10, dtype=np.uint8)


@pytest.fixture
def bright_image() -> np.ndarray:
    return np.full((600, 800, 3), 250, dtype=np.uint8)


@pytest.fixture
def glare_image() -> np.ndarray:
    image = np.full((600, 800, 3), 120, dtype=np.uint8)
    image[100:300, 100:400] = 255  # patch saturé simulant un reflet
    return image


@pytest.fixture
def document_like_image() -> np.ndarray:
    """Rectangle clair au format CR80 (1,586) sur fond texturé, pour tester la détection de contour."""
    rng = np.random.default_rng(7)
    image = rng.integers(0, 40, size=(600, 900, 3), dtype=np.uint8).astype(np.uint8)
    card_w, card_h = 634, 400  # ratio ~1.585
    x, y = (900 - card_w) // 2, (600 - card_h) // 2
    image[y : y + card_h, x : x + card_w] = 230
    return image


@pytest.fixture
def text_image_factory():
    """Génère une image avec du texte imprimé net, pour valider Tesseract de bout en bout
    (installation, langues, pipeline) sans dépendre d'une vraie photo de CNI."""

    def _make(text: str, width: int = 600, height: int = 200) -> np.ndarray:
        image = np.full((height, width, 3), 255, dtype=np.uint8)
        cv2.putText(
            image, text, (20, height // 2 + 10), cv2.FONT_HERSHEY_SIMPLEX, 1.4, (0, 0, 0), 3, cv2.LINE_AA
        )
        return image

    return _make


@pytest.fixture
def patterned_text_image_factory():
    """Génère une image de texte imprimé sur un fond dégradé de couleur avec un motif guilloché
    (fines lignes diagonales répétées) superposé, pour se rapprocher du cas réel qui a motivé le
    retravail de preprocessing.py : CNI camerounaise avec bandes de couleur (vert/rose/jaune) et
    fond guilloché derrière le texte."""

    def _make(text: str, width: int = 700, height: int = 220) -> np.ndarray:
        image = np.zeros((height, width, 3), dtype=np.uint8)
        # Dégradé horizontal vert -> rose -> jaune pâle, similaire aux bandes de couleur d'une CNI.
        stops = [(210, 245, 220), (235, 210, 225), (225, 235, 200)]
        for x in range(width):
            t = x / max(width - 1, 1)
            segment = min(int(t * (len(stops) - 1)), len(stops) - 2)
            local_t = t * (len(stops) - 1) - segment
            color = tuple(
                int(stops[segment][c] * (1 - local_t) + stops[segment + 1][c] * local_t) for c in range(3)
            )
            image[:, x] = color

        # Motif guilloché : fines lignes diagonales répétées, légèrement plus sombres que le fond.
        overlay = image.copy()
        for offset in range(-height, width, 6):
            cv2.line(overlay, (offset, 0), (offset + height, height), (150, 150, 150), 1, cv2.LINE_AA)
        image = cv2.addWeighted(overlay, 0.35, image, 0.65, 0)

        cv2.putText(
            image, text, (20, height // 2 + 10), cv2.FONT_HERSHEY_SIMPLEX, 1.4, (10, 10, 10), 3, cv2.LINE_AA
        )
        return image

    return _make


@pytest.fixture
def sample_portrait_bytes() -> bytes:
    """Vraie photo de visage (échantillon public officiel MediaPipe), utilisée pour les tests qui
    ont besoin d'une détection de visage réelle — les images synthétiques ne suffisent pas."""
    return (_FIXTURES_DIR / "sample_portrait.jpg").read_bytes()


@pytest.fixture
def encode_jpeg():
    def _encode(image: np.ndarray) -> bytes:
        success, buffer = cv2.imencode(".jpg", image)
        assert success
        return buffer.tobytes()

    return _encode
