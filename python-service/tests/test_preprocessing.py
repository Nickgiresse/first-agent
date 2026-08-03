import cv2
import numpy as np

from app.ocr.engine import extract_text
from app.ocr.preprocessing import (
    BYPASS_PROFILE,
    DEFAULT_PROFILE,
    PALE_DOCUMENT_PROFILE,
    PreprocessingProfile,
    preprocess_for_ocr,
)


def test_bypass_profile_only_converts_to_grayscale(text_image_factory):
    image = text_image_factory("ABC")
    result = preprocess_for_ocr(image, BYPASS_PROFILE)
    expected = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    assert result.shape == expected.shape
    assert np.array_equal(result, expected)


def test_default_profile_returns_grayscale_uint8(text_image_factory):
    image = text_image_factory("ABC")
    result = preprocess_for_ocr(image)
    assert result.ndim == 2
    assert result.dtype == np.uint8


def test_pale_document_profile_produces_binary_image(text_image_factory):
    # La binarisation adaptative (profil "document pâle") ne doit produire que deux valeurs.
    image = text_image_factory("ABC")
    result = preprocess_for_ocr(image, PALE_DOCUMENT_PROFILE)
    unique_values = set(np.unique(result).tolist())
    assert unique_values <= {0, 255}


def test_min_channel_mode_differs_from_default_gray(patterned_text_image_factory):
    image = patterned_text_image_factory("NKENG")
    gray_result = preprocess_for_ocr(image, PreprocessingProfile(channel_mode="gray"))
    min_channel_result = preprocess_for_ocr(image, PreprocessingProfile(channel_mode="min_channel"))
    assert not np.array_equal(gray_result, min_channel_result)


def test_unknown_channel_mode_raises():
    import pytest

    image = np.full((100, 100, 3), 200, dtype=np.uint8)
    with pytest.raises(ValueError):
        preprocess_for_ocr(image, PreprocessingProfile(channel_mode="purple"))


def test_correct_perspective_straightens_skewed_document_for_ocr():
    # Carte claire avec texte noir, posée (via warp) en biais sur un fond sombre bruité — imite
    # une photo prise avec un léger angle. La correction de perspective doit permettre à Tesseract
    # de lire le texte malgré l'inclinaison.
    height, width = 500, 700
    rng = np.random.default_rng(11)
    background = rng.integers(0, 40, size=(height, width, 3), dtype=np.uint8)

    card = np.full((260, 400, 3), 235, dtype=np.uint8)
    cv2.putText(card, "MBALLA", (30, 150), cv2.FONT_HERSHEY_SIMPLEX, 2.0, (10, 10, 10), 4, cv2.LINE_AA)

    card_corners = np.array([[0, 0], [399, 0], [399, 259], [0, 259]], dtype=np.float32)
    skewed_corners = np.array([[160, 90], [560, 60], [590, 380], [130, 400]], dtype=np.float32)
    matrix = cv2.getPerspectiveTransform(card_corners, skewed_corners)
    warped_card = cv2.warpPerspective(card, matrix, (width, height))

    mask = np.any(warped_card != 0, axis=2)
    composite = background.copy()
    composite[mask] = warped_card[mask]

    preprocessed = preprocess_for_ocr(composite, PreprocessingProfile(correct_perspective=True))
    text = extract_text(preprocessed)

    assert "MBALLA" in text.upper()
