import numpy as np

from app.config.settings import Settings
from app.vision.detector import find_document_contour, is_fully_visible, matches_id_card_shape


def test_finds_document_contour(document_like_image):
    contour = find_document_contour(document_like_image)
    assert contour is not None
    assert contour.area_ratio > 0.1


def test_matches_id_card_shape_for_cr80_ratio(document_like_image):
    settings = Settings()
    contour = find_document_contour(document_like_image)
    assert matches_id_card_shape(contour, settings) is True


def test_fully_visible_true_when_centered(document_like_image):
    settings = Settings()
    contour = find_document_contour(document_like_image)
    assert is_fully_visible(contour, document_like_image.shape, settings) is True


def test_no_contour_on_blank_image():
    blank = np.full((600, 800, 3), 128, dtype=np.uint8)
    contour = find_document_contour(blank)
    assert contour is None
